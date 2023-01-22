/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.editor.BOLD
import io.nimbly.tzatziki.editor.ITALIC
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl
import java.util.regex.Pattern

private val BOLD_PATTERN = Pattern.compile("[^\\*]*(\\*\\*[^\\*]*\\*\\*)[^\\*]*", Pattern.MULTILINE)
private val STAR_START_PATTERN = Pattern.compile("^[\\s]*(\\*.*$)", Pattern.MULTILINE)

class TzMarkdownAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        if (!TOGGLE_CUCUMBER_PL)
            return

        if (element !is GherkinFeatureHeaderImpl) return

        //
        // Concatenate text
        val text = element.text

        //
        // BOLD
        val bolds: MutableMap<Int, Int> = HashMap()
        var matcher = BOLD_PATTERN.matcher(text)
        while (matcher.find()) {
            val from = element.textOffset + matcher.start(1)
            val to = element.textOffset + matcher.end(1)
            val r = TextRange(from, to)
            holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                .range(r).textAttributes(BOLD).create()
            bolds[from] = r.length
        }

        //
        // BULLETS
        val bullets: MutableList<Int> = ArrayList()
        matcher = STAR_START_PATTERN.matcher(text)
        while (matcher.find()) {
            val group = matcher.group(1)
            if (group.startsWith("**")) continue
            if (group.endsWith("*") && StringUtils.countMatches(group, "*") % 2 == 0) continue
            bullets.add(matcher.start(1))
        }

        //
        // ITALIC
        var i = 0
        var star = -1
        while (i < text.length) {
            if (bullets.contains(i)) {
                star = -1
                i++
                continue
            }
            val c = text[i]
            if (c == '*') {
                if (bolds.containsKey(i)) {
                    star = -1
                    i += bolds[i]!!
                    continue
                }
                star =
                    if (star < 0) {
                        i
                    }
                    else {
                        val from = element.textOffset + star
                        val to = element.textOffset + i
                        val r = TextRange(from, to + 1)
                        holder.newAnnotation(HighlightSeverity.INFORMATION, TZATZIKI_NAME)
                            .range(r).textAttributes(ITALIC).create()
                        -1
                    }
            }
            i++
        }

    }

}