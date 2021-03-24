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

package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.SMART_EDIT
import io.nimbly.tzatziki.mouse.TzSelectionModeManager.disableColumnSelectionMode
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class TzToggleTzatzikiAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent)
        = SMART_EDIT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        SMART_EDIT = !SMART_EDIT
        if (!SMART_EDIT)
            e.getData(CommonDataKeys.EDITOR)?.disableColumnSelectionMode()
    }

    override fun update(event: AnActionEvent) {
        val isVisible = event.getData(CommonDataKeys.PSI_FILE)?.fileType == GherkinFileType.INSTANCE
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = isVisible && editor!=null
        super.update(event)
    }

    override fun isDumbAware()
        = true
}