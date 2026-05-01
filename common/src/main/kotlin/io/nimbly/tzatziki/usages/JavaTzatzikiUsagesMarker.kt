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
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.getNumberIcon
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStep

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
            targets = steps))
    }

    protected fun buildMarker(element: PsiElement, targets: List<GherkinStep>) : RelatedItemLineMarkerInfo<PsiElement> {

        val distinctSteps = targets.toSet()
        // Tooltip & gutter badge count distinct *scenarios* (stable with previous behavior),
        // while the popup lists each individual step occurrence (issue #103).
        val scenarioCount = distinctSteps.map { it.stepHolder }.toSet().size
        val usagesText = if (scenarioCount == 1) "1 scenario" else "$scenarioCount scenarios"
        // Single-ref case: enrich the gutter tooltip with file:line + Feature/Scenario/Step
        // so the user can see the full context without opening the popup.
        val tooltip = if (distinctSteps.size == 1) {
            buildSingleStepTooltip(distinctSteps.first())
        } else {
            usagesText
        }

        val builder = NavigationGutterIconBuilder
            .create(getNumberIcon(scenarioCount, JBColor.foreground()))
            .setTargets(distinctSteps)
            .setTargetRenderer { StepUsageTargetRenderer }
            .setAlignment(GutterIconRenderer.Alignment.RIGHT)
            .setTooltipText(tooltip)
            .setPopupTitle(TZATZIKI_NAME)

        return builder.createLineMarkerInfo(element)
    }

    private fun buildSingleStepTooltip(step: GherkinStep): String {
        val file = step.containingFile
        val location = if (file != null) {
            val doc = PsiDocumentManager.getInstance(step.project).getDocument(file)
            val line = doc?.getLineNumber(step.textOffset)?.plus(1)
            if (line != null) "${file.name}:$line" else file.name
        } else null

        val featureName = PsiTreeUtil.getParentOfType(step, GherkinFeature::class.java)
            ?.featureName?.trim()?.takeIf { it.isNotEmpty() }
        val scenarioName = step.stepHolder.scenarioName?.trim()?.takeIf { it.isNotEmpty() }
        val stepText = "${step.keyword.text.trim()} ${step.name.trim()}".trim()

        val sb = StringBuilder("<html>")
        if (location != null) {
            sb.append("<i>").append(StringUtil.escapeXmlEntities(location)).append("</i><br/>")
        }
        // Two <br/> to force a visible blank line between location and the Feature/Scenario/Step block
        sb.append("<br/>")
        if (featureName != null) {
            sb.append("<b>Feature:</b> ").append(StringUtil.escapeXmlEntities(featureName)).append("<br/>")
        }
        if (scenarioName != null) {
            sb.append("<b>Scenario:</b> ").append(StringUtil.escapeXmlEntities(scenarioName)).append("<br/>")
        }
        sb.append("<b>Step:</b> ").append(StringUtil.escapeXmlEntities(stepText))
        sb.append("</html>")
        return sb.toString()
    }
}

/**
 * Renders a step usage entry in the gutter popup with three info zones,
 * matching the modern Find Usages look and addressing issue #103:
 *  - mainText      = `<keyword> <step text>`
 *  - containerText = scenario name (when present)
 *  - locationText  = `<file.feature>:<line>`
 */
private object StepUsageTargetRenderer : PsiTargetPresentationRenderer<PsiElement>() {

    override fun getPresentation(element: PsiElement): TargetPresentation {
        val step = element as? GherkinStep
            ?: return TargetPresentation.builder(element.toString()).presentation()

        val keyword = step.keyword.text.trim()
        val name = step.name.trim()
        val mainText = if (keyword.isEmpty()) name else "$keyword $name"

        val scenarioName = step.stepHolder.scenarioName?.trim()?.takeIf { it.isNotEmpty() }

        val file = step.containingFile
        val locationText: String? = if (file != null) {
            val doc = PsiDocumentManager.getInstance(step.project).getDocument(file)
            val line = doc?.getLineNumber(step.textOffset)?.plus(1)
            if (line != null) "${file.name}:$line" else file.name
        } else null

        var presentation = TargetPresentation.builder(mainText)
        // Use the .feature file's own icon (the Cucumber icon registered by the Gherkin plugin).
        file?.getIcon(0)?.let { presentation = presentation.icon(it) }
        if (scenarioName != null) presentation = presentation.containerText(scenarioName)
        if (locationText != null) presentation = presentation.locationText(locationText)

        return presentation.presentation()
    }
}