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

package io.nimbly.tzatziki.markdown

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors.HYPERLINK_ATTRIBUTES
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl

val REGX_IMG_HTML = Regex("<img +src *= *['\"]([a-z0-9-_:./]+)['\"]", RegexOption.IGNORE_CASE)
val REGX_IMG_MAKD = Regex("!\\[(.*?)]\\((.*?)\\)")
val REGX_URL_MAKD = Regex("\\[(.*?)]\\((.*?)\\)")

/**
 * Examples:
 *    ![Chart](images/graph.png)
 *    <img src='images/cucumber.png' style='width:70px; position:absolute; top:0; right:0'/>
 *    [higher than ever](https://www.google.com).
 */
class TzUrlAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!TOGGLE_CUCUMBER_PL) return

        when (element) {
            is GherkinFeatureHeaderImpl ->
                applyUrls(element.text, element.textOffset, element, holder)

            is GherkinStepsHolder -> {
                if (element is GherkinFeature) return
                val doc = element.containingFile.viewProvider.document ?: return
                val range = scenarioDescriptionTextRange(element, doc) ?: return
                val text = element.containingFile.text.substring(range.startOffset, range.endOffset)
                applyUrls(text, range.startOffset, element, holder)
            }
        }
    }

    private fun applyUrls(text: String, baseOffset: Int, element: PsiElement, holder: AnnotationHolder) {
        listOf(REGX_IMG_HTML, REGX_URL_MAKD).forEach { reg ->
            reg.findAll(text)
                .toList()
                .mapNotNull { it.groups.last() }
                .filter { !it.range.isEmpty() }
                .forEach { group ->
                    val r = group.range
                    val textRange = TextRange(r.first, r.last + 1).shiftRight(baseOffset)
                    val fullPath = group.value.getRelativePath(element.containingFile)
                    if (fullPath != null) {
                        holder.newAnnotation(HighlightSeverity.INFORMATION, fullPath)
                            .range(textRange).textAttributes(HYPERLINK_ATTRIBUTES).create()
                    } else {
                        holder.newAnnotation(HighlightSeverity.ERROR, "File not found.")
                            .range(textRange).create()
                    }
                }
        }

        // Also colour the label text of `[label](url)` and `![alt](url)`.
        listOf(REGX_URL_MAKD, REGX_IMG_MAKD).forEach { reg ->
            reg.findAll(text).forEach { m ->
                val label = m.groups[1] ?: return@forEach
                if (label.range.isEmpty()) return@forEach
                val r = TextRange(label.range.first, label.range.last + 1).shiftRight(baseOffset)
                holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                    .range(r).textAttributes(HYPERLINK_ATTRIBUTES).create()
            }
        }
    }
}