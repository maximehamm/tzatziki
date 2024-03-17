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
package io.nimbly.tzatziki.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.nimbly.tzatziki.services.*
import io.nimbly.tzatziki.view.features.CucumberPlusFeaturesView
import io.nimbly.tzatziki.view.filters.CucumberPlusFilterTagsView

class CucumberPlusFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val contentFactory = ApplicationManager.getApplication().getService(ContentFactory::class.java)

        val featuresView = CucumberPlusFeaturesView(project)
        toolWindow.contentManager.addContent(
            contentFactory.createContent(featuresView, "Features", false))

        val filterTagsView = CucumberPlusFilterTagsView(project)
        toolWindow.contentManager.addContent(
            contentFactory.createContent(filterTagsView, "Filters", false))

        val tzService = project.getService(TzFileService::class.java)

        tzService.addTagsListener(object : TagsEventListener {
            override fun tagsUpdated(event: TagEvent) {
                featuresView.refreshTags(event.tags)
                if (event.tagsUpdated)
                    filterTagsView.refresh(event.tags)
            }
        })

        tzService.addTagsFilterListener(object : TagsFilterEventListener {
            override fun tagsFilterUpdated(event: TagFilterEvent) {
                featuresView.refreshTags(event.tagsFilter)
            }
        })
    }
}