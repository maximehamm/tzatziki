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
package io.nimbly.tzatziki.view

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.ui.content.ContentFactory
import io.nimbly.tzatziki.view.features.CucumberPlusFeaturesView
import io.nimbly.tzatziki.view.features.DisposalService
import io.nimbly.tzatziki.view.filters.CucumberPlusFilterTagsView
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTag

class CucumberPlusFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // Deprecated : replace by
        //    val contentFactory = ContentFactory.getInstance()
        val contentFactory = ContentFactory.SERVICE.getInstance()

        val featuresView = CucumberPlusFeaturesView(project)
        toolWindow.contentManager.addContent(
            contentFactory.createContent(featuresView, "Features", false))

        val filterTagsView = CucumberPlusFilterTagsView(project)
        toolWindow.contentManager.addContent(
            contentFactory.createContent(filterTagsView, "Filters", false))

        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(filterTagsView, featuresView),
            DisposalService.getInstance(project)
        )
    }

    class PsiChangeListener(
        val filterView: CucumberPlusFilterTagsView,
        val featuresView: CucumberPlusFeaturesView
    ) : PsiTreeChangeListener {

        override fun beforeChildAddition(event: PsiTreeChangeEvent) = Unit
        override fun beforeChildRemoval(event: PsiTreeChangeEvent) = Unit
        override fun beforeChildReplacement(event: PsiTreeChangeEvent) = Unit
        override fun beforeChildMovement(event: PsiTreeChangeEvent) = Unit
        override fun beforeChildrenChange(event: PsiTreeChangeEvent) = Unit
        override fun beforePropertyChange(event: PsiTreeChangeEvent) = Unit

        override fun childAdded(event: PsiTreeChangeEvent) = event(event)
        override fun childMoved(event: PsiTreeChangeEvent) = event(event)
        override fun childRemoved(event: PsiTreeChangeEvent) = event(event)
        override fun propertyChanged(event: PsiTreeChangeEvent) = event(event)

        override fun childReplaced(event: PsiTreeChangeEvent) {
            val tag = event.parent as? GherkinTag
            if (tag != null)
                refresh(filterView.project)
        }

        override fun childrenChanged(event: PsiTreeChangeEvent) {
            val parent = event.parent
                ?: return
            if (parent.containingFile !is GherkinFile)
                return
            refresh(filterView.project)
        }

        private fun event(event: PsiTreeChangeEvent) {
            //NA
        }

        fun refresh(project: Project) {
            DumbService.getInstance(project).smartInvokeLater {
                PsiDocumentManager.getInstance(project).performWhenAllCommitted() {
                    filterView.refresh()
                    featuresView.refreshTags()
                }
            }
        }
    }
}