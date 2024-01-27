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

package io.nimbly.tzatziki.usages

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.ui.JBColor
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.getNumberIcon
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

abstract class TzStepsUsagesMarker : LineMarkerProvider {

    final override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    protected fun buildMarkers(
        token: PsiElement,
        usages: List<PsiReference>,
        annotationText: String,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        // Group usages by regex
        val groupedByRegex: Map<String, List<GherkinStep>> = usages.map { it.element }
            .filterIsInstance<GherkinStep>()
            .flatMap { step ->
                step.findDefinitions()
                    .toSet()
                    .mapNotNull { it.expression }
                    .map { it to step }
            }
            .groupBy { it.first }
            .map { it.key to it.value.map { it.second }.toList() }
            .toMap()

        // Find annotation usages
        val steps = groupedByRegex[annotationText]
            ?: return

        // Add marker
        result.add(buildMarker(
            element = token,
            targets = steps.map { it.stepHolder }))
    }

    protected fun buildMarker(element: PsiElement, targets: List<GherkinStepsHolder>) : RelatedItemLineMarkerInfo<PsiElement> {

        val toSet = targets.toSet()
        val usagesCount = toSet.size
        val usagesText = if (usagesCount == 1) "Used by 1 scenario" else "Used by $usagesCount scenarios"

        val builder = NavigationGutterIconBuilder
            .create(getNumberIcon(usagesCount, JBColor.foreground()))
            .setTargets(toSet)
            .setAlignment(GutterIconRenderer.Alignment.RIGHT)
            .setTooltipText(usagesText)
            .setPopupTitle(TZATZIKI_NAME)

        return builder.createLineMarkerInfo(element)
    }
}