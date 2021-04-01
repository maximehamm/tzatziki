/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.editor.TEST_IGNORED
import io.nimbly.tzatziki.editor.TEST_KO
import io.nimbly.tzatziki.editor.TEST_OK
import io.nimbly.tzatziki.pdf.escape
import io.nimbly.tzatziki.psi.fullRange
import io.nimbly.tzatziki.util.filterValuesNotNull
import io.nimbly.tzatziki.util.textAttribut
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableRowImpl

class TzTestsResultsAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        //println("\n#### Element = ${element.javaClass.simpleName} === ${element.text}")
        if (element is GherkinStep) {
            annotateStep(element, holder)
        } else if (element is GherkinTableRowImpl) {
            annotateRow(element, holder)
        }
    }

    private fun annotateStep(step: GherkinStep, holder: AnnotationHolder) {
        val results = TzTestRegistry.getResults() ?: return
        doAnnotate(results[step], step, holder)
    }

    private fun annotateRow(row: GherkinTableRow, holder: AnnotationHolder) {
        val results = TzTestRegistry.getResults() ?: return
        row.children
            .filterIsInstance<GherkinTableCell>()
            .map { it to results[it] }
            .toMap()
            .filterValuesNotNull()
            .forEach { (cell, tests) ->
                doAnnotate(tests, cell, holder)
            }
    }

    private fun doAnnotate(tests: List<SMTestProxy>, element: GherkinPsiElement, holder: AnnotationHolder) {

        if (tests.isEmpty()) return
        val what = if (element is GherkinTableCell) "step" else "example"
        val textKey: TextAttributesKey
        val tooltip: String
        if (tests.size == 1) {

            // Simple step or a cell
            val test = tests.first()
            textKey = test.textAttribut
            tooltip = when {
                test.isIgnored -> "The $what <u>could not be executed</u>"
                test.isDefect -> test.tooltip()
                else -> "The $what was <u>successful</u>"
            }
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
        }

        // Add annotation
        holder.newAnnotation(HighlightSeverity.INFORMATION, "Cucumber+")
            .range(element.bestRange())
            .tooltip(tooltip)
            .textAttributes(textKey)
            .create()

        // Clear registry
        try {
            TzTestRegistry.getResults()?.remove(element)
        } catch (e: Exception) {
            //In case if concurrent access for example
        }
    }
}

private fun PsiElement.bestRange(): TextRange {

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

private fun SMTestProxy.tooltip(): String {
    var t = stacktrace ?: return "Cucumber test failure"
    t = t.substringBefore("\n")
    t = t.replaceFirst("^(\\w*\\.)*\\w*: ".toRegex(), "")
    return "<html>${t.escape()}</html>"
}