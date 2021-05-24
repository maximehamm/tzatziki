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

package io.nimbly.tzatziki.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.MAIN
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.psi.getCucumberStepDefinition
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzDeprecatedStepAnnotator : Annotator {

    override fun annotate(step: PsiElement, holder: AnnotationHolder) {

        if (!TOGGLE_CUCUMBER_PL)
            return

        if (step !is GherkinStep) return

        val definition = getCucumberStepDefinition(step)
            ?: return

        val element = definition.element
            ?: return

        val deprecated = MAIN().extensionList.find {
            it.isDeprecated(element)
        }

        if (deprecated !=null) {

            val range = TextRange(step.textRange.startOffset + step.text.indexOfFirst { it == ' ' } +1, step.textRange.endOffset)
            holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                .range(range).textAttributes(DEPRECATED).create()
        }

        return
    }
}