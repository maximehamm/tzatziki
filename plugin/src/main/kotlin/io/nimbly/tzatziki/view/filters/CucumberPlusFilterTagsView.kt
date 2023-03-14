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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
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
import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.cucumber.tagexpressions.TagExpressionParser
import io.nimbly.tzatziki.services.Tag
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.services.TzTagService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.*
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

        val blabla = JBPanelWithEmptyText()
        blabla.layout = BorderLayout()
        blabla.border = JBUI.Borders.empty()
        blabla.withEmptyText("")
        blabla.add(
            JBLabel("""<html>
                The selected tags will be used to filter:<br/>
                 &nbsp; ✓ <b>List of features</b> (<i>Java, Kotlin</i>)<br/>
                 &nbsp; ✓ <b>Cucumber tests execution</b> (<i>Java, Kotlin</i>)<br/>
                 &nbsp; ✓ <b>Features exportation to PDF</b><br/>
                </html>""".trimMargin()
            ), BorderLayout.PAGE_START
        )
        blabla.add(
            filterPanel()
        )

        panel.layout = BorderLayout(10, 10)
        panel.border = JBUI.Borders.empty(10)
        panel.withEmptyText("No tags found")
        panel.add(blabla, BorderLayout.PAGE_START)

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

    private fun filterPanel(): JBPanelWithEmptyText {

        val label = JBLabel(ActionIcons.TAG).apply {
            this.text = """<html><b>Select Tags</b>:</html>"""
        }

        // Filter
        val filter = SimpleToolWindowPanel(false, true)
        filter.layout = BorderLayout()
        filter.border = JBUI.Borders.empty()
        filter.withEmptyText("")

        val toolbarGroup = DefaultActionGroup()
        toolbarGroup.add(FilterItAction(project))

        val toolbar = ActionManager.getInstance().createActionToolbar("CucumberPlusFeature", toolbarGroup, true)
        toolbar.targetComponent = filter
        filter.toolbar = toolbar.component

        //
        val main = JBPanelWithEmptyText(GridLayoutManager(1, 2))
        main.border = JBUI.Borders.empty()
        main.add(label, GridConstraints(
            0, 0, 1, 1,
            ANCHOR_NORTHWEST, FILL_BOTH,
            SIZEPOLICY_CAN_GROW, SIZEPOLICY_WANT_GROW,
            Dimension(32, 52), null, null
        ))
        main.add(filter, GridConstraints(
            0, 1, 1, 1,
            ANCHOR_SOUTHWEST, FILL_NONE,
            SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK,
            null, null, null
        ) )

        return main
    }

    fun refresh(tags: SortedMap<String, Tag>) {
        val newTagsPanel = newTagPanel(tags)
        panel.remove(tagsPanel)
        panel.add(newTagsPanel, BorderLayout.CENTER)
        tagsPanel = newTagsPanel
    }

    private fun newTagPanel(tags: SortedMap<String, Tag>): JPanel {

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
            JBLabel(ActionIcons.TAG).apply {
                this.text = """<html><b>You can adapt the selection</b>:<br/></html>"""
            },
            GridConstraints(
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
            }
            else {
                val checked = checks.filter { it.isSelected }.mapNotNull { it.text }.map { "@$it" }
                tSelection.text = checked.joinToString(" or ")
                state.selection = tSelection.text
                state.selectedTags = checked

                state.filterByTags = checked.isNotEmpty()

                val tagService = project.getService(TzTagService::class.java)
                tagService.updateTagsFilter(state.tagExpression())
            }

            try {
                val expression = if (tSelection.text.trim().isEmpty()) null else TagExpressionParser.parse(tSelection.text.trim())
                val tagService = project.getService(TzTagService::class.java)
                tagService.updateTagsFilter(expression)
            } catch (ignored: Exception) {
            }
        }

        // Load previously checked values
        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        checks.forEach { check ->
            check.isSelected = state.selectedTags.contains("@" + check.text)
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

class FilterItAction(val project: Project) : ToggleAction() {
    init {
        this.templatePresentation.text = "Filter per tags"
        this.templatePresentation.icon = AllIcons.General.Filter
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        return state.filterByTags == true
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {

        val exp: Expression?
        if (state) {
            val stateService = ServiceManager.getService(project, TzPersistenceStateService::class.java)
            exp = stateService.tagExpression()
        } else {
            exp = null
        }

        val stateService = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        stateService.filterByTags = state

        val tagService = project.getService(TzTagService::class.java)
        tagService.updateTagsFilter(exp)
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}