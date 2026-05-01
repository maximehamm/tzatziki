/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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
package io.nimbly.tzatziki.view.features.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.psi.impl.source.resolve.ResolveCache
import io.nimbly.tzatziki.services.StepScopeMode
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.view.features.FeaturePanel

/**
 * Toggle in the Cucumber+ tool window toolbar to switch step-indexing scope between
 * AUTO (per-app, see [io.nimbly.tzatziki.services.StepScope]) and OFF (project-wide).
 */
class StepScopeAction(val panel: FeaturePanel) : ToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    init {
        templatePresentation.text = "Auto-scope step indexing per app"
        templatePresentation.description = "Limit step resolution and completion to the .feature file's app folder " +
            "(detected via .cucumber-scope, package.json, pom.xml or build.gradle*). Disable to revert to project-wide indexing."
        templatePresentation.icon = AllIcons.General.Filter
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        return panel.project.getService(TzPersistenceStateService::class.java).stepScope == StepScopeMode.AUTO
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = panel.project.getService(TzPersistenceStateService::class.java)
        service.stepScope = if (state) StepScopeMode.AUTO else StepScopeMode.OFF
        // Drop the resolve cache so existing CucumberStepReference results are recomputed.
        ResolveCache.getInstance(panel.project).clearCache(true)
    }
}
