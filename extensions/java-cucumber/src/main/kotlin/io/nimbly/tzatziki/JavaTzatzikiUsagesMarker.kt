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
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.ui.JBColor
import io.nimbly.tzatziki.psi.description
import io.nimbly.tzatziki.psi.findStepUsages
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.getNumberIcon
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class JavaTzatzikiUsagesMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        elements
            .filterIsInstance<PsiMethod>()
            .forEach { method ->

                val annotations: List<PsiLiteralExpression> = method.annotations
                        .filter { it.resolveAnnotationType()?.qualifiedName?.startsWith("io.cucumber.java") == true }
                        .mapNotNull { it.parameterList.attributes.firstOrNull()?.value as? PsiLiteralExpression }
                if (annotations.isEmpty())
                    return@forEach

                val usages = findStepUsages(method)
                if (usages.isEmpty())
                    return@forEach

                val groupedByRegex: Map<String, List<GherkinStep>>
                    = usages.map { it.element }
                            .filterIsInstance<GherkinStep>()
                            .flatMap { step ->
                                step.findDefinitions()
                                    .toSet()
                                    .mapNotNull { it.expression }
                                    .map { it to step } }
                            .groupBy { it.first }
                            .map { it.key to it.value.map { it.second }.toList() }
                            .toMap()

                annotations.forEach { annotation ->

                    val steps = groupedByRegex[annotation.value]
                    if (steps != null) {

                        val usagesCount = steps.size
                        val usagesText = if (usagesCount == 1) "1 usage" else "$usagesCount usages"

                        val builder = NavigationGutterIconBuilder
                            .create(getNumberIcon(usagesCount, JBColor.foreground()))
                            .setTargets(steps.map { it.stepHolder }.toSet().sortedBy { it.description })
                            .setAlignment(GutterIconRenderer.Alignment.RIGHT)
                            .setTooltipText(usagesText)
                            .setPopupTitle(TZATZIKI_NAME)

                        result.add(builder.createLineMarkerInfo(annotation.firstChild))
                    }
                }
        }
    }
}