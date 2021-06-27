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
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
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
            .filterIsInstance<PsiJavaToken>()
            .forEach { token ->

                // Check context
                val annotation = PsiTreeUtil.getParentOfType(token, PsiAnnotation::class.java)
                    ?: return@forEach
                if (annotation.resolveAnnotationType()?.qualifiedName?.startsWith("io.cucumber.java") != true)
                    return@forEach
                val annotationText = (token.parent as? PsiLiteralExpression)?.value
                    ?: return@forEach
                val method = PsiTreeUtil.getParentOfType(token, PsiMethod::class.java)
                    ?: return@forEach

                // Find method usages
                val usages = findStepUsages(method)
                if (usages.isEmpty())
                    return@forEach

                // Group usages by regex
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

                // Find annotation usages
                val steps = groupedByRegex[annotationText]
                    ?: return@forEach

                // Add marker
                val usagesCount = steps.size
                val usagesText = if (usagesCount == 1) "1 usage" else "$usagesCount usages"

                val builder = NavigationGutterIconBuilder
                    .create(getNumberIcon(usagesCount, JBColor.foreground()))
                    .setTargets(steps.map { it.stepHolder }.toSet().sortedBy { it.description })
                    .setAlignment(GutterIconRenderer.Alignment.RIGHT)
                    .setTooltipText(usagesText)
                    .setPopupTitle(TZATZIKI_NAME)

                result.add(builder.createLineMarkerInfo(token))

        }
    }
}