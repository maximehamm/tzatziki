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
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.pdf.escape
import io.nimbly.tzatziki.psi.bestRange

class TzTestsResultsAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        val steps = TzTestRegistry.steps ?: return
        steps
            .filter { it.element == element }
            .filter { it.test.isDefect || it.test.isPassed }
            .forEach { step: TzTestStep ->

                val textKey = when {
                    step.test.isIgnored -> TEST_IGNORED
                    step.test.isDefect -> TEST_KO
                    else -> TEST_OK
                }

                val tooltip = when {
                    step.test.isIgnored -> "The test could not be executed"
                    step.test.isDefect -> step.test.tooltip()
                    else -> "The test was successful"
                }

                holder.newAnnotation(HighlightSeverity.INFORMATION,
                    "Cucumner+")
                    .range(element.bestRange())
                    .tooltip(tooltip)
                    .textAttributes(textKey)
                    .create()

                try {
                    TzTestRegistry.steps?.remove(step)
                } catch (e: Exception) {
                    //In case if concurrent access
                }
            }
    }
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

