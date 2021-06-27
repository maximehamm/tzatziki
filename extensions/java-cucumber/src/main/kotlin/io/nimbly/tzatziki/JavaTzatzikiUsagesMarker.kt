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

package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.ui.JBColor
import io.nimbly.tzatziki.psi.description
import io.nimbly.tzatziki.psi.findStepUsages
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.getNumberIcon

class JavaTzatzikiUsagesMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        elements
            .filterIsInstance<PsiMethod>()
            .forEach { element ->

                val usages = findStepUsages(element)

                if (usages.isNotEmpty()) {
                    val builder = NavigationGutterIconBuilder
                        .create(getNumberIcon(usages.size, JBColor.foreground()))
                        .setTargets(usages.map { it.stepHolder }.toSet().sortedBy { it.description })
                        .setAlignment(GutterIconRenderer.Alignment.RIGHT)
                        .setTooltipText("${usages.size} usages")
                        .setPopupTitle(TZATZIKI_NAME)

                    result.add(builder.createLineMarkerInfo(element.nameIdentifier ?: element))
                }
        }
    }
}