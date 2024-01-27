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

package io.nimbly.tzatziki.breakpoints

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.findCucumberStepReferences
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzBreakpointMakerProvider : LineMarkerProviderDescriptor() {

    var firstTimeProjects = mutableSetOf<Project>()

    override fun getName(): String {
       return "Step breakpoint"
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>) {

        val project = elements.firstOrNull()?.project
        if (project!=null && !firstTimeProjects.contains(project)) {
            Tzatziki().extensionList.forEach {
                DumbService.getInstance(project).smartInvokeLater {
                    it.initBreakpointListener(project)
                }
            }
            firstTimeProjects.add(project)
        }

        elements.forEach {
            collectNavigationMarkers(it, result)
        }
    }

    private fun collectNavigationMarkers(
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

        val definitions = step.findCucumberStepReferences().flatMap { it.resolveToDefinitions() }
        if (definitions.isEmpty())
            return

        val breakpoint = Tzatziki().extensionList.firstNotNullOfOrNull {
                it.findBreakpoint(element, definitions)
        } ?: return

        val info = RelatedItemLineMarkerInfo<PsiElement>(
            element,
            step.textRange,
            breakpoint.icon,
            { breakpoint.file.name + ' ' + breakpoint.tooltip },
            { event, elt -> breakpoint.navigatable.navigate(true) },
            GutterIconRenderer.Alignment.RIGHT,
            { breakpoint.targets.map { GotoRelatedItem(it) } })


        var userData = breakpoint.getUserData(BKEY)
        if (userData != null) {
            userData.add(element)
        } else {
            userData = mutableSetOf()
            userData.add(element)
            breakpoint.putUserData(BKEY,userData)
        }


        result.add(info)
    }

    companion object {
        val BKEY = Key.create<MutableSet<PsiElement>>("Cucumber+")
    }
    
}