/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki.testdiscovery

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.ui.DebuggerColors
import io.nimbly.tzatziki.editor.TEST_IGNORED
import io.nimbly.tzatziki.editor.TEST_KO
import io.nimbly.tzatziki.editor.TEST_OK
import io.nimbly.tzatziki.pdf.escape
import io.nimbly.tzatziki.psi.fullRange
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.*

object TzTestRegistry {

    private var activeResults = TzTestResult()
    private val highlighters: MutableList<TzHighlight> = mutableListOf()

    fun refresh(results: TzTestResult) {

        // Clone previous results
        val temp = activeResults.clone()

        // Get involved scenario
        val involvedScenarios = results.tests.mapNotNull { it.value.scenario }.toSet()

        // Retain all related to not-involved scenarios
        temp.tests = temp.tests
            .filter { !involvedScenarios.contains(it.value.scenario) }
            .toMutableMap()

        // Add new results
        temp.putAll(results)

        // Add highlights
        temp.tests.forEach { (element, test) ->
            highlighters += highlight(element, results[element])
        }

        this.activeResults = temp
    }

    private fun highlight(element: GherkinPsiElement, tests: Set<SMTestProxy>): MutableList<TzHighlight> {

        val highlights = mutableListOf<TzHighlight>()
        if (tests.isEmpty())
            return highlights

        val document = element.getDocument()
            ?: return highlights

        val editors = EditorFactory.getInstance().getEditors(document, element.project).toList().nullIfEmpty()
            ?: return highlights

        val what = if (element is GherkinTableCell) "step" else "example"
        val textKey: TextAttributesKey
        val tooltip: String
        val stacktrace: String?
        if (tests.size == 1) {

            // Simple step or a cell
            val test = tests.first()
            textKey = test.textAttribut
            tooltip = when {
                test.isIgnored -> "The $what <u>could not be executed</u>"
                test.isDefect -> test.tooltip()
                else -> "The $what was <u>successful</u>"
            }
            stacktrace = test.stacktrace
        }
        else {

            // Step having examples
            val ignored = tests.count { it.isIgnored }
            val ko = tests.count { it.isDefect && !it.isIgnored }
            val ok = tests.size - ignored - ko
            textKey = when {
                ignored == 0 && ko == 0 -> TEST_OK
                ko > 0 -> TEST_KO
                else -> TEST_IGNORED
            }
            tooltip =
                if (textKey == TEST_OK)
                    "The ${what}${if (tests.size>1) "s" else ""} were all ${tests.size} <u>successful</u>"
                else
                    "$ko ${what}${if (ko>1) "s" else ""} with <u>failure</u>,<br/>" +
                            "$ignored ${what}${if (ignored>1) "s" else ""} <u>not executed</u>,<br/>" +
                            "$ok ${what}${if (ok>1) "s" else ""} <u>successful</u>"
            stacktrace = tests.firstOrNull() { it.stacktrace!=null }?.stacktrace
        }

        // Add annotation
        editors.forEach { editor ->
            val range = element.bestRange()
            highlights += TzHighlight(element.containingFile, editor.markupModel, editor.markupModel.addRangeHighlighter(
                textKey,
                range.startOffset,
                range.endOffset,
                DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                HighlighterTargetArea.EXACT_RANGE
            ))
        }

        return highlights
    }

    fun clearHighlighters(file: PsiFile? = null) {
        val h = mutableListOf<TzHighlight>()
        h.addAll(highlighters)

        h.filter { file == null || it.file == file }
         .forEach {
             it.model.removeHighlighter(it.highlight)
             highlighters.remove(it)
        }
    }

    fun cleanTestsResults(file: PsiFile, editor: Editor) {

        if (activeResults.tests.isEmpty())
            return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val scenario = PsiTreeUtil.getContextOfType(element, GherkinStepsHolder::class.java) ?: return

        // Retain all related to not-involved scenarios
        activeResults.tests = activeResults.tests
            .filter { scenario != it.value.scenario }
            .toMutableMap()

    }

    fun cleanAllTestsResults(file: PsiFile) {

        clearHighlighters()

        if (activeResults.tests.isEmpty())
            return

        // Retain all related to not-involved scenarios
        activeResults.tests = activeResults.tests
            .filter { file != it.value.scenario?.containingFile }
            .toMutableMap()

    }

    val results get() = activeResults

    fun hasResults(file: PsiFile): Boolean {
        return activeResults.tests
            .filter { file != it.value.scenario?.containingFile }
            .isNotEmpty()
    }
}

data class TzHighlight(
    val file: PsiFile,
    val model: MarkupModel,
    val highlight: RangeHighlighter
)

class TzTestResult {

    internal var tests = mutableMapOf<GherkinPsiElement, TzTestItem>()

    fun putAll(results: TzTestResult) {
        results.tests.forEach { (elt, item) ->
            item.tests.forEach { t ->
                this[elt] = t
            }
        }
    }

    operator fun set(element: GherkinPsiElement, value: SMTestProxy) {
        var l = tests[element]
        if (l == null) {
            l = TzTestItem(element)
            tests[element] = l
        }
        l.add(value)
    }

    operator fun get(element: GherkinPsiElement): Set<SMTestProxy> {
        return tests[element]?.tests ?: emptySet()
    }

    fun clone(): TzTestResult {
        val tests = this.tests
            .map { it.key to TzTestItem(it.key, it.value.scenario, it.value.tests.toMutableSet()) }
            .toMap()
        val r = TzTestResult()
        r.tests.putAll(tests)
        return r
    }

}

fun SMTestProxy.tooltip(): String {
    var t = this.stacktrace
    if (t.isNullOrBlank())
        return "Cucumber test failure"

    t = t.substringBefore("\n")
    t = t.replaceFirst("^(\\w*\\.)*\\w*: ".toRegex(), "")
    t = t.escape()

    return "<html>$t</html>"
}

fun PsiElement.bestRange(): TextRange {

    if (this is GherkinTableRow)
        return textRange

    if (this is GherkinTableCell)
        return fullRange

    // Start after keyword
    var i = text.indexOf(" ")
    if (i<0)
        return textRange

    val t = text.substring(i)
    val j = t.indexOfFirst { it != ' ' }
    if (j>0)
        i += j

    // End at end ok line
    var length = text.indexOf("\n")
    if (length < 0) length = text.length

    return TextRange(textRange.startOffset + i, textRange.startOffset + length)
}


internal class TzTestItem(
    element: GherkinPsiElement,
    val scenario: GherkinStepsHolder? = element.parentScenario,
    val tests: MutableSet<SMTestProxy> = mutableSetOf()) {

    fun add(testProxy: SMTestProxy) {
        tests.add(testProxy)
    }
}

private val GherkinPsiElement.parentScenario
    get() = PsiTreeUtil.getNonStrictParentOfType(this, GherkinStepsHolder::class.java)