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

package io.nimbly.tzatziki.markdown

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors.HYPERLINK_ATTRIBUTES
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
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

        if (!TOGGLE_CUCUMBER_PL)
            return

        if (element !is GherkinFeatureHeaderImpl)
            return

        val text = element.text
        listOf(REGX_IMG_HTML, REGX_IMG_MAKD, REGX_URL_MAKD).forEach { reg ->
            reg.findAll(text)
                .toList()
                .forEach { result ->
                    val r = result.groups.last()!!.range
                    val textRange = TextRange(r.first, r.last + 1).shiftRight(element.textOffset)
                    val fullPath = result.groupValues.last().getRelativePath(element.containingFile)
                    if (fullPath != null) {
                        holder.newAnnotation(HighlightSeverity.INFORMATION, fullPath)
                            .range(textRange).textAttributes(HYPERLINK_ATTRIBUTES).create()
                    } else {
                        holder.newAnnotation(HighlightSeverity.ERROR, "File not found.")
                            .range(textRange).create()
                    }
                }
        }
    }

}