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

package io.nimbly.tzatziki.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import io.cucumber.tagexpressions.Expression
import io.cucumber.tagexpressions.TagExpressionParser

@Service(Service.Level.PROJECT)
@State(name = "ProjectCucumberState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TzPersistenceStateService : PersistentStateComponent<TzPersistenceStateService> {

    internal var selectedTags: List<String> = emptyList()
    internal var selection: String? = null

    internal var sourcePathOnly: Boolean? = null
    internal var groupTag: Boolean? = null
    internal var filterByTags: Boolean? = null

    /**
     * Step-indexing scope (issue #104). When AUTO, Cucumber+ narrows step
     * resolution and completion to the "app folder" containing the .feature file
     * — auto-detected by walking up from the feature looking for a marker file
     * (.cucumber-scope > package.json > pom.xml > build.gradle*).
     * When OFF, behaves as before (project-wide indexing).
     */
    var stepScope: StepScopeMode = StepScopeMode.AUTO

    /** True once the "drop a .cucumber-scope file" balloon has been shown for this project. */
    var stepScopeBalloonShown: Boolean = false

    override fun getState(): TzPersistenceStateService {
        return this
    }

    override fun loadState(state: TzPersistenceStateService) {
        this.selection = state.selection
        this.selectedTags = state.selectedTags
        this.groupTag = state.groupTag
        this.filterByTags = state.filterByTags
        this.sourcePathOnly = state.sourcePathOnly
        this.stepScope = state.stepScope
        this.stepScopeBalloonShown = state.stepScopeBalloonShown
    }

    fun tagExpression(): Expression? {
        if (selection.isNullOrBlank())
            return null

        return try {
            TagExpressionParser.parse(selection)
        } catch (ignored: Exception) {
            null
        }
    }

    companion object {
        fun getInstance(): TzPersistenceStateService {
            return ApplicationManager.getApplication().getService(TzPersistenceStateService::class.java)
        }
    }
}

enum class StepScopeMode {
    /** Auto-detect the step indexing scope per .feature file (default). */
    AUTO,
    /** No scoping — project-wide indexing (legacy behavior). */
    OFF
}