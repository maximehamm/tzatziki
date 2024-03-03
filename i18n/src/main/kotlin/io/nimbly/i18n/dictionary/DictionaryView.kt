/*
 * TRANSLATION +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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
package io.nimbly.i18n.dictionary

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBUI
import icons.ActionI18nIcons
import io.nimbly.i18n.util.clearInlays
import io.nimbly.i18n.util.getLeafAtCursor
import io.nimbly.i18n.util.getSelectedTextWithLeadingSpaces
import io.nimbly.i18n.util.playAudio
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Cursor.HAND_CURSOR
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.event.HyperlinkEvent

class DictionaryView(val project: Project) : SimpleToolWindowPanel(true, false), DictionaryListener {

    private val panel = JBPanelWithEmptyText()

    private lateinit var tSelection: JBTextField
    private lateinit var tDefinition: JEditorPane
    private lateinit var sDefinition: JBScrollPane

    private var editor: Editor? = null
    private var document: Document? = null
    private var startOffset: Int? = null
    private var endOffset: Int? = null

    private val searchDefinitionAction = object : AbstractAction("Search definition", ActionI18nIcons.DICO) {
        override fun actionPerformed(e: ActionEvent?) {
            searchDefinition()
        }
    }.apply { isEnabled = false }

    init {
        setContent(initPanel())

        EditorFactory.getInstance()
            .eventMulticaster
            .addCaretListener(object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    refresh(event);
                }
            }, ApplicationManager.getApplication())

        DictionaryManager.registerListener(this)

        tSelection.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                searchDefinitionAction.isEnabled = true
            }
        })
    }

    fun searchDefinition() {

        val txt = tSelection.text
          ?: return

        val definition = DictionaryManager.searchDefinition(txt)
        if (definition.status == EStatut.NOT_FOUND) {
            tDefinition.text = "No definition found"
        }
        else {
            val html = generateHtml(definition.result!!)
            tDefinition.text = html
        }

        tDefinition.caretPosition = 0

        editor?.apply {

            EditorFactory.getInstance().clearInlays()

            val start = startOffset
            val end = endOffset
            if (start != null && end != null) {
                this.selectionModel.setSelection(start, end)
            }
        }
    }


    fun refresh(event: CaretEvent) {

        val editor = event.editor

        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {

                val text = editor.selectionModel.getSelectedTextWithLeadingSpaces()
                if (text != null && text.trim().isNotEmpty()) {

                    this.tSelection.text = text.trimIndent()
                    this.startOffset = editor.selectionModel.selectionStart
                    this.endOffset = editor.selectionModel.selectionEnd
                    this.document = editor.document
                    this.editor = editor

                    searchDefinitionAction.isEnabled = true
                }
                else {

                    val literal = editor.getLeafAtCursor()
                    if (literal != null && literal.text.trim().isNotEmpty()) {
                        this.tSelection.text = literal.text.trimIndent()
                        this.startOffset = literal.startOffset
                        this.endOffset = literal.endOffset
                        this.document = editor.document
                        this.editor = editor

                        searchDefinitionAction.isEnabled = true
                    }
                    else {
                        searchDefinitionAction.isEnabled = !tSelection.text.isEmpty()
                    }
                }

                if (tSelection.text.isEmpty())
                    tDefinition.text = ""
            }
        }
    }

    private fun initPanel(): JPanel {

        val main = JBPanelWithEmptyText(GridLayoutManager(6, 4))
        main.border = JBUI.Borders.empty()
        main.withEmptyText("")
        main.add(
            JBLabel(
                """<html>
                English dictionary 🇬🇧 :<br/>
                 &nbsp; ✓ <b>Select some text in the editor</b> (<i>Gherkin, Javascript...</i>)<br/>
                 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>or put cursor into the string to use</b><br/>
                </html>""".trimMargin()
            ),
            GridConstraints(
                0, 0, 1, 4,
                ANCHOR_NORTHWEST, FILL_BOTH,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        // Selection
        main.add(
            JBLabel("Selection :"),
            GridConstraints(
                2, 0, 1, 2,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        tSelection = JBTextField()
        main.add(
            tSelection, GridConstraints(
                3, 0, 1, 4,
                ANCHOR_NORTHWEST, FILL_HORIZONTAL,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK,
                null, null, null
            )
        )

        // Definition
        main.add(
            JButton(searchDefinitionAction),
            GridConstraints(
                4, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        tDefinition = JEditorPane()
        tDefinition.editorKit = JBHtmlEditorKit() // HTMLEditorKitBuilder.simple()
        tDefinition.isEditable = false // Set the JEditorPane to be non-editable
        tDefinition.contentType = "text/html" // Set the content type
        tDefinition.cursor = Cursor(HAND_CURSOR)

        tDefinition.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                playAudio(e.url)
            }
        }

        sDefinition = JBScrollPane(
            tDefinition,
            VERTICAL_SCROLLBAR_AS_NEEDED,
            HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        main.add(
            sDefinition, GridConstraints(
                5, 0, 1, 4,
                ANCHOR_NORTHWEST, FILL_BOTH,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                null, null, null
            )
        )

        panel.layout = BorderLayout(10, 10)
        panel.border = JBUI.Borders.empty(10)
        panel.withEmptyText("No literal selected yet found")
        panel.add(main, BorderLayout.CENTER)

        return panel
    }

    override fun onDefinition(event: DictionaryEvent) {

        searchDefinitionAction.isEnabled = true

        val html =
            if (event.definition.status == EStatut.FOUND)
                generateHtml(event.definition.result!!)
            else
                "<h1>No definition found</h1>"
        tDefinition.text = html
        tDefinition.caretPosition = 0

    }
}
