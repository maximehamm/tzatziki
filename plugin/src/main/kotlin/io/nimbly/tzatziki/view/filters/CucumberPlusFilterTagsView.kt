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
package io.nimbly.tzatziki.view.filters

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import io.nimbly.tzatziki.services.Tag
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.services.TzTagService
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

class CucumberPlusFilterTagsView(val project: Project) : SimpleToolWindowPanel(true, false) {

    val panel = JBPanelWithEmptyText()
    lateinit var tagsPanel: JPanel

    init {
        setContent(initPanel())
    }

    private fun initPanel(): JPanel {

        panel.layout = BorderLayout(10, 10)
        panel.border = JBUI.Borders.empty(10)
        panel.withEmptyText("No tags found")

        panel.add(
            JBLabel(
                """<html>
            The selected tags will be used to filter:<br/>
             &nbsp; ✓ <b>Cucumber tests execution</b> (<i>Java, Kotlin</i>)<br/>
             &nbsp; ✓ <b>Features exportation to PDF</b><br/><br/>
            <b>Select Tags</b>:
            </html>""".trimMargin()
            ), BorderLayout.PAGE_START
        )

        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted{

                // First tag list initialization
                val tagService = project.getService(TzTagService::class.java)
                val tags = tagService.getTags()
                tagsPanel = newTagPanel(tags)
                panel.add(tagsPanel, BorderLayout.CENTER)
            }
        }

        return panel
    }

    fun refresh(tags: Map<String, Tag>) {
        val newTagsPanel = newTagPanel(tags)
        panel.remove(tagsPanel)
        panel.add(newTagsPanel, BorderLayout.CENTER)
        tagsPanel = newTagsPanel
    }

    private fun newTagPanel(tags: Map<String, Tag>): JPanel {

        // Tags
        val checks = mutableListOf<JBCheckBox>()
        val pTags = JBPanelWithEmptyText(WrapLayout(FlowLayout.LEFT))
        tags.forEach { (tag, _) ->
            val t = JBCheckBox(tag)
            pTags.add(t)
            checks.add(t)
        }
        val sTags = JBScrollPane(pTags, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
        sTags.border = BorderFactory.createEmptyBorder()

        // Main
        val main = JBPanelWithEmptyText(GridLayoutManager(4, 1))
        main.add(
            sTags, GridConstraints(
                0, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_BOTH,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                null, null, null
            )
        )

        // Selection label
        main.add(
            JBLabel("""<html><b>You can adapt the selection</b>:<br/></html>""".trimMargin()), GridConstraints(
                1, 0, 1, 1,
                ANCHOR_SOUTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        // Selection
        val tSelection = JBTextArea(4, 10).apply { lineWrap = true; wrapStyleWord = true }
        val sSelection = JBScrollPane(tSelection, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
        main.add(
            sSelection, GridConstraints(
                2, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_HORIZONTAL,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                null, null, null
            )
        )

        // Example label
        main.add(
            JBLabel(
                """<html>
                For example: <i>@tag1 and not @tag2&nbsp;</i></html>""".trimMargin()
            ), GridConstraints(
                3, 0, 1, 1,
                ANCHOR_SOUTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        // Update selection function
        fun updateSelection(selectionOnly: Boolean) {
            val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
            if (selectionOnly) {
                state.selection = tSelection.text
            } else {
                val checked = checks.filter { it.isSelected }.map { it.text }.filterNotNull()
                tSelection.text = checked.joinToString(" or ")
                state.selection = tSelection.text
                state.selectedTags = checked
            }
        }

        // Load previously checked values
        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
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

        return main
    }
}
