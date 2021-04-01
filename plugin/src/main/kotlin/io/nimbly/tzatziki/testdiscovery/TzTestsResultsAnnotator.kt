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
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.pdf.escape
import io.nimbly.tzatziki.psi.fullRange
import io.nimbly.tzatziki.util.filterValuesNotNull
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

        val tests = TzTestRegistry.tests ?: return
        val test = tests[step] ?: return

        doAnnotate(test, step, holder)
    }

    private fun annotateRow(row: GherkinTableRow, holder: AnnotationHolder) {

        val tests = TzTestRegistry.tests ?: return
        row.children
            .filterIsInstance<GherkinTableCell>()
            .map { it to tests[it] }
            .toMap()
            .filterValuesNotNull()
            .forEach { (cell, test) ->
                doAnnotate(test, cell, holder)
            }
    }

    private fun doAnnotate(test: SMTestProxy, element: GherkinPsiElement, holder: AnnotationHolder) {

        val textKey = when {
            test.isIgnored -> TEST_IGNORED
            test.isDefect -> TEST_KO
            else -> TEST_OK
        }
        val tooltip = when {
            test.isIgnored -> "The test could not be executed"
            test.isDefect -> test.tooltip()
            else -> "The test was successful"
        }
        holder.newAnnotation(
            HighlightSeverity.INFORMATION,
            "Cucumber+"
        )
            .range(element.bestRange())
            .tooltip(tooltip)
            .textAttributes(textKey)
            .create()
        try {
            TzTestRegistry.tests?.remove(element)
        } catch (e: Exception) {
            //In case if concurrent access
        }
    }
}

private fun PsiElement.bestRange(): TextRange {

    if (this is GherkinTableRow)
        return textRange

    if (this is GherkinTableCell)
        return fullRange

    var i = text.indexOf(" ")
    if (i<0)
        return textRange

    val t = text.substring(i)
    val j = t.indexOfFirst { it != ' ' }
    if (j>0)
        i += j

    return TextRange(textRange.startOffset + i, textRange.endOffset)
}

private fun SMTestProxy.tooltip(): String {
    var t = stacktrace ?: return "Cucumber test failure"
    t = t.substringBefore("\n")
    t = t.replaceFirst("^(\\w*\\.)*\\w*: ".toRegex(), "")
    return "<html>${t.escape()}</html>"
}

val TEST_KO = TextAttributesKey.createTextAttributesKey("CCP_TEST_KO", DefaultLanguageHighlighterColors.STRING)
val TEST_OK = TextAttributesKey.createTextAttributesKey("CCP_TEST_OK", DefaultLanguageHighlighterColors.STRING)
val TEST_IGNORED = TextAttributesKey.createTextAttributesKey("CCP_TEST_IGNORED", DefaultLanguageHighlighterColors.STRING)

