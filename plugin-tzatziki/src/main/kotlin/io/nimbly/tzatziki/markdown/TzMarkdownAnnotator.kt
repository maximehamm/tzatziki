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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.editor.BOLD
import io.nimbly.tzatziki.editor.CODE
import io.nimbly.tzatziki.editor.ITALIC
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.countMatches
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl
import java.util.regex.Pattern

private val BOLD_PATTERN = Pattern.compile("[^\\*]*(\\*\\*[^\\*]*\\*\\*)[^\\*]*", Pattern.MULTILINE)
private val STAR_START_PATTERN = Pattern.compile("^[\\s]*(\\*.*$)", Pattern.MULTILINE)
private val CODE_PATTERN = Pattern.compile("`([^`\\n]+)`")

class TzMarkdownAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!TOGGLE_CUCUMBER_PL) return

        when (element) {
            is GherkinFeatureHeaderImpl ->
                applyMarkdown(element.text, element.textOffset, holder)

            is GherkinStepsHolder -> {
                // Scenario / Scenario Outline / Background / Rule: the description text
                // lives between the keyword line and the first step. We only style THAT
                // slice — never the keyword/name line, never the steps themselves.
                val doc = element.containingFile.viewProvider.document ?: return
                val range = scenarioDescriptionTextRange(element, doc) ?: return
                applyMarkdown(
                    element.containingFile.text.substring(range.startOffset, range.endOffset),
                    range.startOffset,
                    holder
                )
            }
        }
    }

    /**
     * Apply bold / italic / inline-code annotations on [text] whose first char sits at
     * absolute offset [baseOffset] in the document.
     */
    private fun applyMarkdown(text: String, baseOffset: Int, holder: AnnotationHolder) {

        // BOLD — `**…**` runs come first so single-star italic doesn't mis-match them.
        val bolds: MutableMap<Int, Int> = HashMap()
        var matcher = BOLD_PATTERN.matcher(text)
        while (matcher.find()) {
            val from = baseOffset + matcher.start(1)
            val to = baseOffset + matcher.end(1)
            val r = TextRange(from, to)
            holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                .range(r).textAttributes(BOLD).create()
            bolds[from] = r.length
        }

        // BULLETS — `* item` at line start; excluded from italic parsing.
        val bullets: MutableList<Int> = ArrayList()
        matcher = STAR_START_PATTERN.matcher(text)
        while (matcher.find()) {
            val group = matcher.group(1)
            if (group.startsWith("**")) continue
            if (group.endsWith("*") && countMatches(group, "*") % 2 == 0) continue
            bullets.add(matcher.start(1))
        }

        // ITALIC — single `*…*` pairs.
        var i = 0
        var star = -1
        while (i < text.length) {
            if (bullets.contains(i)) { star = -1; i++; continue }
            val c = text[i]
            if (c == '*') {
                if (bolds.containsKey(baseOffset + i)) { star = -1; i += bolds[baseOffset + i]!!; continue }
                star = if (star < 0) i
                else {
                    val from = baseOffset + star
                    val to = baseOffset + i
                    val r = TextRange(from, to + 1)
                    holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                        .range(r).textAttributes(ITALIC).create()
                    -1
                }
            }
            i++
        }

        // INLINE CODE — `…`
        val codeMatcher = CODE_PATTERN.matcher(text)
        while (codeMatcher.find()) {
            val from = baseOffset + codeMatcher.start()
            val to = baseOffset + codeMatcher.end()
            holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                .range(TextRange(from, to)).textAttributes(CODE).create()
        }
    }
}
