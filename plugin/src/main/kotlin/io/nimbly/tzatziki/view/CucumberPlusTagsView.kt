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

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.WrapLayout
import io.nimbly.tzatziki.settings.CucumberPersistenceState
import io.nimbly.tzatziki.util.findAllTags
import io.nimbly.tzatziki.util.getGherkinScope
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.border.EmptyBorder

class CucumberPlusTagsView(private val project: Project)
    : SimpleToolWindowPanel(true, false) {

    init {
        setContent(initTagsPanel())
    }

    private fun initTagsPanel(): JPanel {

        val p = JBPanelWithEmptyText()
        p.layout = BorderLayout(10, 10)
        p.border = EmptyBorder(10, 10, 10, 10)
        p.withEmptyText("No tags found")

        p.add(JBLabel("""<html>
            The selected tags will be used to filter:<br/>
             &nbsp; ✓ <b>Cucumber tests execution</b> (<i>Java, Kotlin</i>)<br/>
             &nbsp; ✓ <b>Features exportation to PDF</b><br/><br/>
            <b>Select Tags</b>:
            </html>""".trimMargin()), BorderLayout.PAGE_START)

        lateinit var tagsPanel: JPanel
        lateinit var tagsList: List<String>

        fun refresh() {
            DumbService.getInstance(project).smartInvokeLater {
                PsiDocumentManager.getInstance(project).performWhenAllCommitted() {
                    val newTagsPanel = newTagPanel(tagsList)
                        ?: return@performWhenAllCommitted
                    p.remove(tagsPanel)
                    p.add(newTagsPanel.first, BorderLayout.CENTER)
                    tagsPanel = newTagsPanel.first
                    tagsList = newTagsPanel.second
                }
            }
        }

        DumbService.getInstance(project).smartInvokeLater {

            // First tag list initialization
            val newTagPanel = newTagPanel(null)
            tagsPanel = newTagPanel!!.first
            tagsList = newTagPanel.second
            p.add(tagsPanel, BorderLayout.CENTER)

            // Listen to file refreshing
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = refresh()
            }, project)
        }

        return p
    }

    private fun newTagPanel(currentTags: List<String>?): Pair<JPanel, List<String>>? {

        // Get all tags
        val tags = findAllTags(project, project.getGherkinScope())
            .groupBy { it.name }
            .keys
            .map { "@$it" }
            .sortedBy { it.toUpperCase() }

        // Check if tags are still the same
        if (tags == currentTags)
            return null

        // Tags
        val checks = mutableListOf<JBCheckBox>()
        val pTags = JBPanelWithEmptyText(WrapLayout(FlowLayout.LEFT))
        tags.forEach { tag ->
            val t = JBCheckBox(tag)
            pTags.add(t)
            checks.add(t)
        }
        val sTags = JBScrollPane(pTags, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
        sTags.border = BorderFactory.createEmptyBorder()

        // Main
        val main = JBPanelWithEmptyText(GridLayoutManager(4, 1))
        main.add(sTags, GridConstraints(
            0, 0, 1, 1,
            ANCHOR_NORTHWEST, FILL_BOTH,
            SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            null, null, null))

        // Selection label
        main.add(JBLabel("""<html><b>You can adapt the selection</b>:<br/></html>""".trimMargin()), GridConstraints(
            1, 0, 1, 1,
            ANCHOR_SOUTHWEST, FILL_NONE,
            SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
            null, null, null))

        // Selection
        val tSelection = JBTextArea(4, 10).apply { lineWrap = true; wrapStyleWord = true }
        val sSelection = JBScrollPane(tSelection, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
        main.add(sSelection, GridConstraints(
            2, 0, 1, 1,
            ANCHOR_NORTHWEST, FILL_HORIZONTAL,
            SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
            null, null, null))

        // Example label
        main.add(JBLabel("""<html>
                For example: <i>@tag1 and not @tag2&nbsp;</i></html>""".trimMargin()), GridConstraints(
            3, 0, 1, 1,
            ANCHOR_SOUTHWEST, FILL_NONE,
            SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
            null, null, null))

        // Update selection function
        fun updateSelection(selectionOnly: Boolean) {
            val state = ServiceManager.getService(project, CucumberPersistenceState::class.java)
            if (selectionOnly) {
                state.selection = tSelection.text
            }
            else {
                val checked = checks.filter { it.isSelected }.map { it.text }.filterNotNull()
                tSelection.text = checked.joinToString(" or ")
                state.selection = tSelection.text
                state.selectedTags = checked
            }
        }

        // Load previously checked values
        val state = ServiceManager.getService(project, CucumberPersistenceState::class.java)
        checks.forEach { check ->
            check.isSelected = state.selectedTags.contains(check.text)
        }
        tSelection.text = state.selection

        // Setup listeners
        checks.forEach { check ->
            check.addItemListener {
                updateSelection(false)
            }
        }
        tSelection.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                updateSelection(true)
            }
        })

        return Pair(main, tags)
    }
}