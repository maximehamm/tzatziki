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
package io.nimbly.i18n.translation

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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.ActionI18nIcons.I18N
import io.nimbly.i18n.util.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent.SELECTED
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicComboBoxEditor

private val ComboBox<Lang>.lang: Lang
    get() = this.selectedItem as Lang

class TranslateView(val project: Project) : SimpleToolWindowPanel(true, false), TranslationListener {

    private val panel = JBPanelWithEmptyText()

    private lateinit var tSelection: JBTextArea
    private lateinit var tTranslation: JBTextArea

    private val inputLanguage = ComboBox(CollectionComboBoxModel<Lang>())
    private val outputLanguage = ComboBox(CollectionComboBoxModel<Lang>())

    private var outputFlagIcon: Icon? = null

    private var editor: Editor? = null
    private var document: Document? = null
    private var startOffset: Int? = null
    private var endOffset: Int? = null

    private val translateAction = object : AbstractAction("Translate", outputFlagIcon) {
        override fun actionPerformed(e: ActionEvent?) {
            translate()
        }
    }.apply { isEnabled = false }

    private val replaceAction = object : AbstractAction("Replace selection", AllIcons.Actions.MenuPaste) {
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

        val isoCodes = languagesMap.map { Lang(it.key, it.value) }.sortedBy { it.name }

        val input = PropertiesComponent.getInstance().getValue(SAVE_INPUT, "auto")
        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "en")

        inputLanguage.apply {
            val model = this.model as CollectionComboBoxModel<Lang>
            model.add(Lang("auto", "Automatic"))
            isoCodes.forEach {
                model.add(isoCodes)
            }
            this.setRenderer(IsoCodesRenderer(inputLanguage.editor))
            this.editor = IsoCodesComboBoxEditor()

            model.selectedItem = model.items.find { it.code == input }
        }

        outputLanguage.apply {
            val model = this.model as CollectionComboBoxModel<Lang>
            isoCodes.forEach {
                model.add(isoCodes)
            }
            this.setRenderer(IsoCodesRenderer(outputLanguage.editor))
            this.editor = IsoCodesComboBoxEditor()

            model.selectedItem = model.items.find { it.code == output }
        }


        outputFlagIcon = TranslationIcons.getFlag(output.lowercase()) ?: I18N

        translateAction.putValue(Action.SMALL_ICON, outputFlagIcon)

        inputLanguage.addItemListener { e ->
            if (e.stateChange == SELECTED) {
                val lang = e.item as Lang
                PropertiesComponent.getInstance().setValue(SAVE_INPUT, lang.code)
            }
        }
        outputLanguage.addItemListener { e ->
            if (e.stateChange == SELECTED) {
                val lang = e.item as Lang
                PropertiesComponent.getInstance().setValue(SAVE_OUTPUT, lang.code)
                outputFlagIcon = TranslationIcons.getFlag(lang.code)
                translateAction.putValue(Action.SMALL_ICON, outputFlagIcon)
            }
        }

        TranslationManager.registerListener(this)
    }

    fun translate() {

        val txt = tSelection.text
          ?: return

        val translation = TranslationManager.translate(
            (outputLanguage.selectedItem as Lang).code,
            (inputLanguage.selectedItem as Lang).code,
            txt)
          ?: return

        tTranslation.text = translation.translated.trimIndent()
        replaceAction.isEnabled = true

        editor?.apply {

            EditorFactory.getInstance().clearInlays()

            val start = startOffset
            val end = endOffset
            if (start != null && end != null) {
                this.selectionModel.setSelection(start, end)
            }
        }
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

            EditorFactory.getInstance().clearInlays()
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
                    this.translateAction.isEnabled = true
                }
                else {

                    val literal = editor.getLeafAtCursor()
                    if (literal != null && literal.text.trim().isNotEmpty()) {
                        this.tSelection.text = literal.text.trimIndent()
                        this.startOffset = literal.startOffset
                        this.endOffset = literal.endOffset
                        this.document = editor.document
                        this.editor = editor
                        this.translateAction.isEnabled = true
                    }
                    else {
                        this.tSelection.text = ""
                        translateAction.isEnabled = false
                    }
                }
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

        val languages = JBPanelWithEmptyText(GridLayoutManager(2, 2, Insets(0, 0, 0, 0), 30 , 0))
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
                0, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        languages.add(
            JBLabel("Output language :"),
            GridConstraints(
                0, 1, 1, 1,
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
            outputLanguage,
            GridConstraints(
                1, 1, 1, 1,
                ANCHOR_WEST, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
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

        tSelection = TextArea()
        main.add(
            JBScrollPane(tSelection, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED),
            GridConstraints(
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

        tTranslation = TextArea()
        main.add(
            JBScrollPane(tTranslation, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED),
            GridConstraints(
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

        if (inputLanguage.lang.code == "auto") {
            val model = inputLanguage.model as CollectionComboBoxModel<Lang>
            val find = model.items.find { it.code == event.translation.sourceLanguageIndentified }
            if (find != null) {
                inputLanguage.selectedItem = find
            }
        }
    }
}

class IsoCodesRenderer(editor: ComboBoxEditor) : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

        if (value is Lang) {

            val originalIcon: Icon
            text = value.name

            if (value.code == "auto") {
                originalIcon =  TranslationIcons.getFlag(" ")!!
                font = font.deriveFont(Font.BOLD)
            } else {
                originalIcon =  TranslationIcons.getFlag(value.code)!!
                font = font.deriveFont(Font.PLAIN)
            }

            val img = BufferedImage(18, originalIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
            val g2d = img.createGraphics()
            originalIcon.paintIcon(this, g2d, 0, 0)
            g2d.dispose()

            icon = ImageIcon(img)
        }

        border = BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding())

        return this
    }
}

class IsoCodesComboBoxEditor : BasicComboBoxEditor() {

    private val label = JBLabel()
    private val panel = JBPanelWithEmptyText(BorderLayout())

    init {
        panel.add(label, BorderLayout.CENTER)
        panel.isOpaque = true
        panel.background = UIUtil.getListBackground()
    }

    override fun getEditorComponent(): Component {
        return panel
    }

    override fun getItem(): Any? {
        return label.text
    }

    override fun setItem(anObject: Any?) {
        if (anObject is Lang) {
            label.text = anObject.name
            val originalIcon = TranslationIcons.getFlag(anObject.code)!!
            val image = BufferedImage(18, originalIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
            val g2d = image.createGraphics()
            originalIcon.paintIcon(this.panel, g2d, 0, 0)
            g2d.dispose()
            label.icon = ImageIcon(image)
        }
    }

    override fun selectAll() {
        // Do nothing or implement text selection if needed
    }

    override fun addActionListener(l: ActionListener) {
        // This might not be needed depending on your requirements
    }

    override fun removeActionListener(l: ActionListener) {
        // This might not be needed depending on your requirements
    }
}

data class Lang(val code: String, val name: String) {
    override fun toString(): String {
        return name
    }
}

class TextArea : JBTextArea(15, 10) {
    init {
        lineWrap = true;
        wrapStyleWord = true;
        border = EmptyBorder(Insets(5, 5, 5, 5))
        font = JBFont.create(JBUI.Fonts.label().deriveFont(12))
    }
}