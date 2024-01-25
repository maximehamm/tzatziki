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
package io.nimbly.tzatziki.view.i18n

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
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
import icons.ActionIcons
import io.nimbly.tzatziki.util.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.event.DocumentEvent

const val SAVE_INPUT = "io.nimbly.tzatziki.translation.input"
const val SAVE_OUTPUT = "io.nimbly.tzatziki.translation.output"

class CucumberPlusI18nView(val project: Project) : SimpleToolWindowPanel(true, false) {

    val panel = JBPanelWithEmptyText()

    lateinit var tSelection: JBTextArea
    lateinit var tTranslation: JBTextArea

    val inputLanguage = JBTextField("auto")
    val outputLanguage: JBTextField = JBTextField("EN")
    var flag: Icon? = null

    var document: Document? = null
    var startOffset: Int? = null
    var endOffset: Int? = null

    val translateAction = object : AbstractAction("Translate", flag) {
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

        flag = I18NIcons.getFlag(outputLanguage.text.trim().lowercase()) ?: ActionIcons.I18N
        translateAction.putValue(Action.SMALL_ICON, flag)

        inputLanguage.text = input
        outputLanguage.text = output

        inputLanguage.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                PropertiesComponent.getInstance().setValue(SAVE_INPUT, inputLanguage.text)
            }
        })
        outputLanguage.document.addDocumentListener(object : DocumentAdapter() {

            override fun textChanged(e: DocumentEvent) {
                PropertiesComponent.getInstance().setValue(SAVE_OUTPUT, outputLanguage.text)

                flag = I18NIcons.getFlag(outputLanguage.text.trim().lowercase()) ?: ActionIcons.I18N
                translateAction.putValue(Action.SMALL_ICON, flag)

                translateAction.isEnabled = translateAction.isEnabled && (flag != null)
            }
        })
    }

    fun translate() {

        val translation = googleTranslate(
            outputLanguage.text.lowercase(),
            inputLanguage.text.lowercase(),
            tSelection.text)
            ?: return

        tTranslation.text = translation
        replaceAction.isEnabled = true
    }

    fun replace() {

        executeWriteCommand(project, "Translating with Cucumber+") {

            val start = startOffset ?: return@executeWriteCommand
            val end = endOffset ?: return@executeWriteCommand
            val translation = tTranslation.text ?: return@executeWriteCommand

            document?.replaceString(start, end, translation)

            replaceAction.isEnabled = false
        }
    }

    fun refresh(event: CaretEvent) {

        val editor = event.editor

        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {

                val text = editor.selectionModel.getSelectedText(false)
                if (text != null && text.trim().isNotEmpty()) {
                    tSelection.text = text
                    startOffset = editor.selectionModel.selectionStart
                    endOffset = editor.selectionModel.selectionEnd
                    document = editor.document
                    translateAction.isEnabled = flag != null
                }
                else {

                    val literal = editor.getLeafAtCursor()
                    if (literal != null && literal.text.trim().isNotEmpty()) {
                        tSelection.text = literal.text
                        startOffset = literal.startOffset
                        endOffset = literal.endOffset
                        document = editor.document
                        translateAction.isEnabled = flag != null
                    }
                    else {
                        translateAction.isEnabled = false
                    }
                }

                tTranslation.text = ""
                replaceAction.isEnabled = false
            }
        }
    }

    private fun initPanel(): JPanel {

        val main = JBPanelWithEmptyText(GridLayoutManager(7, 2))
        main.border = JBUI.Borders.empty()
        main.withEmptyText("")
        main.add(
            JBLabel("""<html>
                Translating text :<br/>
                 &nbsp; ✓ <b>Set the 'input' language as iso code or use "auto"</b><br/>
                 &nbsp; ✓ <b>Set the 'output' language as iso code</b><br/>
                 &nbsp; ✓ <b>Select some text in the editor</b> (<i>Gherkin, Java, etc.</i>)<br/>
                 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>or put cursor into the string literal to translate</b><br/>
                 <br/>
                </html>""".trimMargin()
            ),
            GridConstraints(
                0, 0, 1, 2,
                ANCHOR_NORTHWEST, FILL_BOTH,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        main.add(
            JBLabel("Input language :"),
            GridConstraints(
                1, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        main.add(
            JBLabel("Output language :"),
            GridConstraints(
                1, 1, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        main.add(
            inputLanguage,
            GridConstraints(
                2, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_HORIZONTAL,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, Dimension(60, 30)
            )
        )
        main.add(
            outputLanguage,
            GridConstraints(
                2, 1, 1, 1,
                ANCHOR_NORTHWEST, FILL_HORIZONTAL,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, Dimension(50, 30)
            )
        )

        // Selection
        main.add(
            JBLabel("Selection :"),
            GridConstraints(
                3, 0, 1, 2,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_GROW, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        tSelection = JBTextArea(15, 10).apply {
            lineWrap = true; wrapStyleWord = true }
        val sSelection = JBScrollPane(tSelection,
            VERTICAL_SCROLLBAR_AS_NEEDED,
            HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        main.add(
            sSelection, GridConstraints(
                4, 0, 1, 2,
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
                5, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        main.add(
            JButton(replaceAction),
            GridConstraints(
                5, 1, 1, 1,
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
                6, 0, 1, 2,
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
}
