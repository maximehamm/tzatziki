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
package io.nimbly.i18n

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.ActionI18nIcons.I18N
import io.nimbly.i18n.util.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.event.DocumentEvent

class TranslateView(val project: Project) : SimpleToolWindowPanel(true, false), TranslationListener {

    private val panel = JBPanelWithEmptyText()

    private lateinit var tSelection: JBTextArea
    private lateinit var tTranslation: JBTextArea

    private val inputLanguage = JBTextField("auto")
    private val outputLanguage = JBTextField("EN")

    private val inputFlag = JBLabel()
    private val outputFlag = JBLabel()

    private var editor: Editor? = null
    private var document: Document? = null
    private var startOffset: Int? = null
    private var endOffset: Int? = null

    val translateAction = object : AbstractAction("Translate", outputFlag.icon) {
        override fun actionPerformed(e: ActionEvent?) {
            translate()
        }
    }.apply { isEnabled = false }

    val replaceAction = object : AbstractAction("Replace selection", AllIcons.Actions.MenuPaste) {
        override fun actionPerformed(e: ActionEvent?) {
            replace()
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

        val input = PropertiesComponent.getInstance().getValue(SAVE_INPUT, "auto")
        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")

        inputLanguage.text = input
        outputLanguage.text = output

        outputFlag.text = flagsMap.getOrDefault(outputLanguage.text.trim().lowercase(), "⛔")
        translateAction.putValue(Action.NAME, outputFlag.text + " Translate")

        inputFlag.text = flagsMap.getOrDefault(inputLanguage.text.trim().lowercase(), "")


        inputLanguage.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                PropertiesComponent.getInstance().setValue(SAVE_INPUT, inputLanguage.text)
                inputFlag.text = flagsMap.getOrDefault(inputLanguage.text.trim().lowercase(), "")
            }
        })
        outputLanguage.document.addDocumentListener(object : DocumentAdapter() {

            override fun textChanged(e: DocumentEvent) {
                PropertiesComponent.getInstance().setValue(SAVE_OUTPUT, outputLanguage.text)

                outputFlag.text = flagsMap.getOrDefault(outputLanguage.text.trim().lowercase(), "⛔")

                translateAction.putValue(Action.NAME, outputFlag.text + " Translate")
                translateAction.isEnabled = translateAction.isEnabled && (outputFlag.text != "⛔")
            }
        })

        TranslationManager.registerListener(this)
    }

    fun translate() {

        val txt = tSelection.text
          ?: return

        val translation = TranslationManager.translate(
            outputLanguage.text.lowercase(),
            inputLanguage.text.lowercase(),
            txt)
          ?: return

        tTranslation.text = translation.translated.trimIndent()
        replaceAction.isEnabled = true

        inputFlag.text = flagsMap.getOrDefault(translation.sourceLanguageIndentified.lowercase(), "⛔")

        editor?.clearInlays()
    }

    fun replace() {

        executeWriteCommand(project, "Translating with Cucumber+") {

            val start = startOffset ?: return@executeWriteCommand
            val end = endOffset ?: return@executeWriteCommand
            val translation = tTranslation.text ?: return@executeWriteCommand
            val doc = document ?: return@executeWriteCommand

            val text = doc.getText(TextRange(start, end))

            var indented = translation.indentAs(text)

            doc.replaceString(start, end, indented)

            replaceAction.isEnabled = false

            editor?.clearInlays()
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
                    this.translateAction.isEnabled = outputFlag.text != "⛔"
                }
                else {

                    val literal = editor.getLeafAtCursor()
                    if (literal != null && literal.text.trim().isNotEmpty()) {
                        this.tSelection.text = literal.text.trimIndent()
                        this.startOffset = literal.startOffset
                        this.endOffset = literal.endOffset
                        this.document = editor.document
                        this.editor = editor
                        this.translateAction.isEnabled = outputFlag.text != "⛔"
                    }
                    else {
                        translateAction.isEnabled = false
                    }
                }

                tTranslation.text = ""
                replaceAction.isEnabled = false

                if (inputLanguage.text == "auto")
                    inputFlag.text = ""
            }
        }
    }

    private fun initPanel(): JPanel {

        val main = JBPanelWithEmptyText(GridLayoutManager(6, 4))
        main.border = JBUI.Borders.empty()
        main.withEmptyText("")
        main.add(
            JBLabel("""<html>
                Translating text :<br/>
                 &nbsp; ✓ <b>Set the 'input' language as iso code or use "auto"</b><br/>
                 &nbsp; ✓ <b>Set the 'output' language as iso code</b><br/>
                 &nbsp; ✓ <b>Select some text in the editor</b> (<i>Gherkin, Java, etc.</i>)<br/>
                 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>or put cursor into the string literal to translate</b><br/>
                 <br/>
                </html>""".trimMargin()
            ),
            GridConstraints(
                0, 0, 1, 4,
                ANCHOR_NORTHWEST, FILL_BOTH,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        val languages = JBPanelWithEmptyText(GridLayoutManager(2, 4, Insets(0, 0, 0, 0), 30 , 0))
        main.add(languages,
            GridConstraints(
                1, 0, 1, 2,
                ANCHOR_WEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        languages.add(
            JBLabel("Input language :"),
            GridConstraints(
                0, 0, 1, 2,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        languages.add(
            JBLabel("Output language :"),
            GridConstraints(
                0, 2, 1, 2,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        languages.add(
            inputLanguage,
            GridConstraints(
                1, 0, 1, 1,
                ANCHOR_WEST, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        languages.add(
            inputFlag,
            GridConstraints(
                1, 1, 1, 1,
                ANCHOR_WEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        languages.add(
            outputLanguage,
            GridConstraints(
                1, 2, 1, 1,
                ANCHOR_WEST, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        languages.add(
            outputFlag,
            GridConstraints(
                1, 3, 1, 1,
                ANCHOR_WEST, FILL_NONE,
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
        tSelection = JBTextArea(15, 10).apply {
            lineWrap = true;
            wrapStyleWord = true
        }
        val sSelection = JBScrollPane(tSelection,
            VERTICAL_SCROLLBAR_AS_NEEDED,
            HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        main.add(
            sSelection, GridConstraints(
                3, 0, 1, 4,
                ANCHOR_NORTHWEST, FILL_BOTH,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                null, null, null
            )
        )

        // Translation
        main.add(
            JButton(translateAction),
            GridConstraints(
                4, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        main.add(
            JButton(replaceAction),
            GridConstraints(
                4, 1, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        tTranslation = JBTextArea(15, 10).apply {
            lineWrap = true; wrapStyleWord = true }
        val sTranslation = JBScrollPane(tTranslation,
            VERTICAL_SCROLLBAR_AS_NEEDED,
            HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        main.add(
            sTranslation, GridConstraints(
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

    override fun onTranslation(event: TranslationEvent) {

        translateAction.isEnabled = true
        replaceAction.isEnabled = true

        tTranslation.text = event.translation.translated.trimIndent()

        if (inputLanguage.text == "auto")
            inputFlag.text = flagsMap.getOrDefault(event.translation.sourceLanguageIndentified, "")
    }
}
