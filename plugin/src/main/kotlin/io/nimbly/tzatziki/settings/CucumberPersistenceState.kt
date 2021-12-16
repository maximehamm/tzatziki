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

package io.nimbly.tzatziki.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import io.cucumber.tagexpressions.Expression
import io.cucumber.tagexpressions.TagExpressionParser


@State(name = "ProjectCucumberState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CucumberPersistenceState : PersistentStateComponent<CucumberPersistenceState> {

    var selectedTags: List<String> = emptyList()
    var selection: String? = null

    override fun getState(): CucumberPersistenceState {
        return this
    }

    override fun loadState(state: CucumberPersistenceState) {
        this.selection = state.selection
        this.selectedTags = state.selectedTags
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
        fun getInstance(): CucumberPersistenceState {
            return ApplicationManager.getApplication().getService(CucumberPersistenceState::class.java)
        }
    }
}