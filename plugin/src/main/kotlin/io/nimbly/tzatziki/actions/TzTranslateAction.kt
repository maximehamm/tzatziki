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

package io.nimbly.tzatziki.actions

import com.google.common.io.Resources
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.properties.references.I18nUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import icons.ActionIcons.I18N
import io.nimbly.tzatziki.util.*
import io.nimbly.tzatziki.view.i18n.SAVE_OUTPUT
import java.io.File


class TzTranslateAction : AnAction() , DumbAware {

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor!=null

        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")

        event.presentation.icon =  I18NIcons.getFlag(output.trim().lowercase())
            ?: I18N
        super.update(event)
    }

    override fun actionPerformed(event: AnActionEvent) {

        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor =  CommonDataKeys.EDITOR.getData(event.dataContext) ?: return

        val startOffset: Int
        val endOffset: Int
        val text: String?
        if (editor.selectionModel.hasSelection()) {
            startOffset = editor.selectionModel.selectionStart
            endOffset = editor.selectionModel.selectionEnd
            text = editor.selectionModel.getSelectedText(false)
        }
        else {
            val offset = CommonDataKeys.CARET.getData(event.dataContext)?.offset ?: return
            val l = file.findElementAt(offset) ?: return
            startOffset = l.textRange.startOffset
            endOffset = l.textRange.endOffset
            text = l.text
        }

        if (text == null)
            return

        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")
        val translation = googleTranslate(
            output, "auto", text)
            ?: return

        executeWriteCommand(file.project, "Translating with Cucumber+", Runnable {
            file.getDocument()?.replaceString(startOffset, endOffset, translation)
        })
    }

    override fun isDumbAware()
        = true
}