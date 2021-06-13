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

package io.nimbly.tzatziki.breakpoints

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import io.nimbly.tzatziki.TZATZIKI
import io.nimbly.tzatziki.findCucumberStepReferences
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzBreakpointMakerProvider : RelatedItemLineMarkerProvider() {

    var firstTimeProjects = mutableSetOf<Project>()

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {

        if (element !is LeafElement)
            return

        if (element.containingFile !is GherkinFile)
            return

        val step = element.parent as? GherkinStep
            ?: return
        if (step.keyword != element.node)
            return

        if (!firstTimeProjects.contains(element.project)) {
            TZATZIKI().extensionList.forEach {
                it.initBreakpointListener(element.project) }
            firstTimeProjects.add(element.project)
        }

        val definitions = step.findCucumberStepReferences().flatMap { it.resolveToDefinitions() }
        if (definitions.isEmpty())
            return

        val breakpoint = TZATZIKI().extensionList.firstNotNullResult {
                it.findBreakpoint(element, definitions)
        } ?: return

        val info = RelatedItemLineMarkerInfo<PsiElement>(
            element,
            step.textRange,
            breakpoint.icon,
            { breakpoint.tooltip },
            { event, elt -> breakpoint.navigatable.navigate(true) },
            GutterIconRenderer.Alignment.RIGHT,
            { breakpoint.targets.map { GotoRelatedItem(it) } })

        result.add(info)
    }
    
}