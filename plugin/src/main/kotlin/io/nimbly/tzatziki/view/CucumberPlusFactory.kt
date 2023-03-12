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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.nimbly.tzatziki.services.*
import io.nimbly.tzatziki.view.features.CucumberPlusFeaturesView
import io.nimbly.tzatziki.view.filters.CucumberPlusFilterTagsView

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

        val tagService = project.getService(TzTagService::class.java)

        tagService.addTagsListener(object : TagsEventListener {
            override fun tagsUpdated(event: TagEvent) {
                filterTagsView.refresh(event.tags)
                featuresView.refreshTags(event.tags)
            }
        })

        tagService.addTagsFilterListener(object : TagsFilterEventListener {
            override fun tagsFilterUpdated(event: TagFilterEvent) {
                featuresView.refreshTags(event.tagsFilter)
            }
        })

    }
}