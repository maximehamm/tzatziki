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
package io.nimbly.tzatziki.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import io.nimbly.tzatziki.services.StepScopeMode
import io.nimbly.tzatziki.services.TzPersistenceStateService
import javax.swing.JCheckBox
import javax.swing.JComponent

class TzCucumberPlusConfigurable(private val project: Project) : Configurable {

    private var autoScopeCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "Cucumber+"

    override fun createComponent(): JComponent {
        val state = project.getService(TzPersistenceStateService::class.java)

        return panel {
            group("Step indexing scope (issue #104)") {
                row {
                    val cb = checkBox("Auto-scope step indexing per app folder")
                        .comment(
                            "When enabled, step resolution (Cmd+Click), completion and Find Usages " +
                                "are limited to the app folder containing the current <code>.feature</code> file." +
                                "<br/>The app folder is auto-detected by walking up and looking for a marker file:" +
                                "<br/>&nbsp;&nbsp;<code>.cucumber-scope</code> &gt; <code>package.json</code> &gt; <code>pom.xml</code> &gt; <code>build.gradle*</code>." +
                                "<br/>Drop a <code>.cucumber-scope</code> empty file at the desired root to override the detection."
                        )
                    autoScopeCheckbox = cb.component
                    cb.component.isSelected = state.stepScope == StepScopeMode.AUTO
                }.layout(RowLayout.PARENT_GRID)
            }
        }
    }

    override fun isModified(): Boolean {
        val state = project.getService(TzPersistenceStateService::class.java)
        val current = state.stepScope == StepScopeMode.AUTO
        return autoScopeCheckbox?.isSelected != current
    }

    override fun apply() {
        val state = project.getService(TzPersistenceStateService::class.java)
        val newMode = if (autoScopeCheckbox?.isSelected == true) StepScopeMode.AUTO else StepScopeMode.OFF
        if (state.stepScope != newMode) {
            state.stepScope = newMode
            // Drop the resolve cache so existing CucumberStepReference results are recomputed.
            ResolveCache.getInstance(project).clearCache(true)
        }
    }

    override fun reset() {
        val state = project.getService(TzPersistenceStateService::class.java)
        autoScopeCheckbox?.isSelected = state.stepScope == StepScopeMode.AUTO
    }
}
