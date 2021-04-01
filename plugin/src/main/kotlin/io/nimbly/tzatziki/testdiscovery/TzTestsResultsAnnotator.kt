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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
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
        val test = results[step] ?: return
        doAnnotate(test, step, holder)
    }

    private fun annotateRow(row: GherkinTableRow, holder: AnnotationHolder) {
        val results = TzTestRegistry.getResults() ?: return
        row.children
            .filterIsInstance<GherkinTableCell>()
            .map { it to results[it] }
            .toMap()
            .filterValuesNotNull()
            .forEach { (cell, test) ->
                doAnnotate(test, cell, holder)
            }
    }

    private fun doAnnotate(test: SMTestProxy, element: GherkinPsiElement, holder: AnnotationHolder) {
        val textKey = test.textAttribut
        val tooltip = when {
            test.isIgnored -> "The test could not be executed"
            test.isDefect -> test.tooltip()
            else -> "The test was successful"
        }
        holder.newAnnotation(HighlightSeverity.INFORMATION, "Cucumber+")
            .range(element.bestRange())
            .tooltip(tooltip)
            .textAttributes(textKey)
            .create()
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