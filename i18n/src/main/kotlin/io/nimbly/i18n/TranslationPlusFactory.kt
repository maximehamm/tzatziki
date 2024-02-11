/*
 * TRANSLATION +
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
package io.nimbly.i18n

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.nimbly.i18n.dictionary.DictionaryView
import io.nimbly.i18n.translation.TranslateView

class TranslationPlusFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val contentFactory = ApplicationManager.getApplication().getService(ContentFactory::class.java)

        toolWindow.contentManager.addContent(
            contentFactory.createContent(TranslateView(project), "Translation", false)
        )

        toolWindow.contentManager.addContent(
            contentFactory.createContent(DictionaryView(project), "Dictionary", false)
        )

    }
}