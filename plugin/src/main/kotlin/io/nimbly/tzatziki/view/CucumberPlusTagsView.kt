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
package io.nimbly.tzatziki.view

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.WrapLayout
import io.nimbly.tzatziki.psi.getGherkinScope
import io.nimbly.tzatziki.settings.CucumberPersistenceState
import io.nimbly.tzatziki.util.findAllTags
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
                Only the scenarios with the selected tags will be triggered.<br/><br/>
                <b>Select Tags</b> :
                </html>""".trimMargin()), BorderLayout.PAGE_START)

        lateinit var tagsPanel: JPanel

        fun refresh() {
            DumbService.getInstance(project).smartInvokeLater {
                PsiDocumentManager.getInstance(project).performWhenAllCommitted() {
                    val newTagsPanel = newTagPanel()
                    p.remove(tagsPanel)
                    p.add(newTagsPanel, BorderLayout.CENTER)
                    tagsPanel = newTagsPanel
                }
            }
        }

        DumbService.getInstance(project).smartInvokeLater {

            // First tag list initialization
            tagsPanel = newTagPanel()
            p.add(tagsPanel, BorderLayout.CENTER)

            // Listen to file refreshing
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = refresh()
            }, project)
        }

        return p
    }

    private fun newTagPanel(): JPanel {

        val tags: List<String> = findAllTags(project, project.getGherkinScope())
            .groupBy { it.name }
            .keys
            .map { "@$it" }
            .sortedBy { it.toUpperCase() }

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
        val main = JBPanelWithEmptyText(GridLayoutManager(3, 1))
        main.add(sTags, GridConstraints(
            0, 0, 1, 1,
            GridConstraints.ANCHOR_NORTHWEST, FILL_BOTH,
            SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            null, null, null))

        // Selection label
        main.add(JBLabel("""<html>
                <b>Selection</b> :<br/>
                </html>""".trimMargin()), GridConstraints(
            1, 0, 1, 1,
            GridConstraints.ANCHOR_SOUTHWEST, GridConstraints.FILL_NONE,
            SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))

        // Selection
        val tSelection = JBTextArea(4, 10).apply { lineWrap = true; wrapStyleWord = true }
        val sSelection = JBScrollPane(tSelection, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
        main.add(sSelection, GridConstraints(
            2, 0, 1, 1,
            GridConstraints.ANCHOR_NORTHWEST, FILL_HORIZONTAL,
            SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
            null, null, null))

        // Update selection function
        fun updateSelection() {

            // Update selection text
            val checked = checks.filter { it.isSelected }.map { it.text }.filterNotNull()
            tSelection.text = checked.joinToString(" or ")

            // Save to settings
            val state = ServiceManager.getService(project, CucumberPersistenceState::class.java)
            state.selection = tSelection.text
            state.selectedTags = checked
        }

        // Load previously checked values
        val state = ServiceManager.getService(project, CucumberPersistenceState::class.java)
        checks.forEach { check ->
            check.isSelected = state.selectedTags.contains(check.text)
        }
        tSelection.text = state.selection

        // Setup check boxes listeners
        checks.forEach { check ->
            check.addItemListener {
                updateSelection()
            }
        }

        return main
    }

    companion object {
        private val DATA_KEY: DataKey<List<String>> = DataKey.create("selectedTags")
        private val KEY = Key.create<Set<String>?>("CucumberPlusTagsView")

    }
}