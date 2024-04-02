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
package io.nimbly.i18n.translation.view

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.RefactoringUiService
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.suggested.endOffset
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import icons.ActionI18nIcons
import icons.ActionI18nIcons.I18N
import io.nimbly.i18n.translation.*
import io.nimbly.i18n.translation.engines.Lang
import io.nimbly.i18n.translation.engines.google.SAVE_INPUT
import io.nimbly.i18n.translation.engines.google.SAVE_OUTPUT
import io.nimbly.i18n.util.*
import java.awt.*
import java.awt.event.*
import java.awt.event.ItemEvent.SELECTED
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.border.EmptyBorder


private val ComboBox<Lang>.lang: Lang
    get() = this.selectedItem as Lang

class TranslateView : SimpleToolWindowPanel(true, false), TranslationListener {

    private var ctxt = Context()
    private val refactoringModel = RefactoringSetup()

    private val panel = JBPanelWithEmptyText()

    private lateinit var tSelection: JBTextArea
    private lateinit var tTranslation: JBTextArea

    private val inputLanguage = ComboBox(CollectionComboBoxModel<Lang>())
    private val outputLanguage = ComboBox(CollectionComboBoxModel<Lang>())

    private var inputLanguageAutoPrefered = false
    private var inputLanguageProgramaticSelection = false;
    private var outputFlagIcon: Icon? = null

    private val refactoringText = object : JBLabel("") {

        private var count: Int? = null
        private var fileCount: Int? = null
        private var singleFileName: String? = null

        fun refresh(usages: Set<PsiElement>? = null, origin: PsiElement? = null) {

            if (usages != null) {
                count = usages.count()
                fileCount = usages.distinctBy { it.containingFile }.size

                val f = usages.distinctBy { it.containingFile }.singleOrNull()?.containingFile
                singleFileName =
                    when (f) {
                        null -> null
                        ctxt.selectedElement?.containingFile -> " in this file"
                        else -> " in file “${f.name}”"
                    } 
            }

            if (count == null || count == 0) {
                this.text = ""
                return
            }

            var t =  " "
            if ((count ?: 0) > 1 && !RefactoringSetup().useRefactoring) {
                t += "⚠ "
                this.foreground = Color.RED
            }
            else {
                this.foreground = JBLabel().foreground
            }

            t += "Found ${count} usage${count.plural}"

            if (singleFileName != null)
                t += singleFileName
            else
                t += " in $fileCount file${fileCount.plural}"

            this.text = t
        }
    }
    private val refactoring = JBCheckBox("Replace using refactoring")
    private val refactoringPreview = JBCheckBox("Show preview")
    private val refactoringSearchInComments = JBCheckBox("Search in comments")

    private val translateAction = object : AbstractAction("Translate", outputFlagIcon) {
        override fun actionPerformed(e: ActionEvent) {
            translate()
        }
    }.apply { isEnabled = false }

    private val replaceAction = object : AbstractAction("Replace", AllIcons.Actions.MenuPaste) {
        override fun actionPerformed(e: ActionEvent) {
            replace()
        }
        fun refresh() {
            val text =
                if (!refactoringModel.useRefactoring)
                    "Replace"
                else if (refactoringModel.preview)
                    "Preview refactoring"
                else
                    "Replace and refactor"
            putValue(Action.NAME, text)
        }
    }.apply { isEnabled = false }

    init {
        setContent(initPanel())

        EditorFactory.getInstance()
            .eventMulticaster
            .addCaretListener(object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    refresh(event.editor)
                }
            }, ApplicationManager.getApplication())

        EditorFactory.getInstance()
            .eventMulticaster
            .addSelectionListener(object : SelectionListener {
                override fun selectionChanged(event: SelectionEvent) {
                    refresh(event.editor)
                }
            }, ApplicationManager.getApplication())

        tSelection.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                translateAction.isEnabled = true
                replaceAction.isEnabled = false
                ctxt.selectedElement = null
                ctxt.startOffset = 0
                ctxt.endOffset = 0
                restoreAutomatic()
            }
        })

        tSelection.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                tSelection.highlighter.removeAllHighlights()
            }
        })

        val isoCodes = languagesMap.map { Lang(it.key, it.value) }.sortedBy { it.name }

        val input = PropertiesComponent.getInstance().getValue(SAVE_INPUT, Lang.AUTO.code)
        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, Lang.DEFAULT.code)

        inputLanguage.apply {
            val model = this.model as CollectionComboBoxModel<Lang>
            model.add(Lang.AUTO)
            model.add(isoCodes)
            this.setRenderer(IsoCodesRenderer())
            this.editor = IsoCodesComboBoxEditor()

            model.selectedItem = model.items.find { it.code == input }
        }

        inputLanguageAutoPrefered = (input == Lang.AUTO.code)

        outputLanguage.apply {
            val model = this.model as CollectionComboBoxModel<Lang>
            model.add(isoCodes)
            this.setRenderer(IsoCodesRenderer())
            this.editor = IsoCodesComboBoxEditor()

            model.selectedItem = model.items.find { it.code == output }
        }

        outputFlagIcon = TranslationIcons.getFlag(output.lowercase()) ?: I18N

        translateAction.putValue(Action.SMALL_ICON, outputFlagIcon)

        inputLanguage.addItemListener { e ->
            if (e.stateChange == SELECTED) {
                val lang = e.item as Lang
                PropertiesComponent.getInstance().setValue(SAVE_INPUT, lang.code)
                if (!inputLanguageProgramaticSelection) {
                    inputLanguageAutoPrefered = (Lang.AUTO == lang)
                    this.inputLanguage.font = this.inputLanguage.font.deriveFont(Font.PLAIN)
                }
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


        replaceAction.refresh()

        TranslationManager.registerListener(this)
    }

    fun translate() {

        val txt = tSelection.text
            ?: return

        val translation = TranslationManager.translate(
            (outputLanguage.selectedItem as Lang).code,
            (inputLanguage.selectedItem as Lang).code,
            txt,
            ctxt.format,
            ctxt.style,
            Origin.from(ctxt.selectedElement, ctxt.editor),
            ctxt.project
        )
        ?: return

        tTranslation.text = translation.translated.trimIndent()

        replaceAction.isEnabled = ctxt.hasSelection()

        val project = ctxt.project
        val editor = ctxt.editor
        if (project != null && editor != null) {
            TranslateAction().doActionPerformed(
                project = project,
                editor = editor,
                file = ctxt.selectedElement?.containingFile,
                editorImpl = ctxt.editor as? EditorImpl,
                caret = ctxt.startOffset,
                isVCS = false,
                withInlineTranslation = false
            )
        }
    }

    fun replace() {

        EditorFactory.getInstance().clearInlays(ctxt.project)

        val translation = tTranslation.text?.escapeFormat(ctxt.format) ?: return

        if (ctxt.selectedElement != null && ctxt.editor!=null) {

            var elt = ctxt.selectedElement.findRenamable()
            if (refactoringModel.useRefactoring && elt != null && canRename(elt)) {

                ctxt.editor!!.caretModel.moveToOffset(ctxt.startOffset!! + 1)

                elt = RenamePsiElementProcessor.forElement(elt).substituteElementToRename(elt, ctxt.editor)?.findRenamable()  ?: elt
                if (refactoringModel.preview) {
                     val d = RefactoringUiService.getInstance()
                         .createRenameRefactoringDialog(elt.project, elt, elt, ctxt.editor)
                     d.performRename(translation)
                }
                else {
                    val rename = RefactoringFactory.getInstance(elt.project)
                        .createRename(elt, translation,
                            refactoringModel.searchInComments,
                            refactoringModel.useRefactoring)
                    val usages = rename.findUsages()
                    rename.doRefactoring(usages)
                }

                return
            }
        }

        val  project = ctxt.project
        if (project != null) {
            executeWriteCommand(project, "Translation+") {

                val start = ctxt.startOffset ?: return@executeWriteCommand
                val end = ctxt.endOffset ?: return@executeWriteCommand
                val doc = ctxt.document ?: return@executeWriteCommand
                val text = doc.getText(TextRange(start, end))
                val indented = translation.indentAs(text)

                doc.replaceString(start, end, indented)

                replaceAction.isEnabled = false

                EditorFactory.getInstance().clearInlays(ctxt.project)
            }
        }
    }

    fun swapLanguages() {
        val input = if (this.inputLanguage.lang.isAuto()) Lang.DEFAULT else this.inputLanguage.lang
        val output = this.outputLanguage.lang

        this.inputLanguage.selectedItem = output
        this.outputLanguage.selectedItem = input
    }

    fun refresh(editor: Editor) {

        restoreAutomatic()
        val project = editor.project ?: return

        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {

                if (project.isDisposed)
                    return@performWhenAllCommitted

                val text = editor.selectionModel.getSelectedTextWithLeadingSpaces()
                if (text != null && text.trim().isNotEmpty()) {

                    val literal = editor.getLeafAtSelection()
                    if (literal != null && literal.startOffset == editor.selectionModel.selectionStart && literal.endOffset == editor.selectionModel.selectionEnd) {
                        ctxt.selectedElement = literal
                    }
                    else {
                        val literal2 = editor.getLeafAtCursor()
                        if (literal2 == null || literal2.startOffset != ctxt.startOffset || literal2.endOffset != ctxt.endOffset)
                            ctxt.selectedElement = null
                    }

                    ctxt.format = editor.detectFormat()
                    ctxt.style = text.trim().removeQuotes().detectStyle(ctxt.selectedElement != null)

                    this.tSelection.textAndSelect = text.trimIndent().unescapeFormat(ctxt.format, true)
                    ctxt.startOffset = editor.selectionModel.selectionStart
                    ctxt.endOffset = editor.selectionModel.selectionEnd
                    ctxt.document = editor.document
                    ctxt.editor = editor

                    this.translateAction.isEnabled = true
                }
                else {

                    val literal = editor.getLeafAtCursor()
                    if (literal != null && literal.text.trim().isNotEmpty()) {
                        ctxt.selectedElement = literal
                        ctxt.format = editor.detectFormat()
                        ctxt.style = literal.text.removeQuotes().detectStyle(true)
                        this.tSelection.textAndSelect = literal.text.trimIndent().unescapeFormat(ctxt.format, true)
                        ctxt.startOffset = literal.startOffset
                        ctxt.endOffset = literal.endOffset
                        ctxt.document = editor.document
                        ctxt.editor = editor
                        this.translateAction.isEnabled = true
                    }
                    else {
                        ctxt.selectedElement = null
                        ctxt.format = EFormat.TEXT
                        ctxt.style = EStyle.NORMAL
                        if (tSelection.text.isEmpty()) {
                            this.tSelection.text = ""
                            translateAction.isEnabled = false
                        }
                    }
                }

                val refactoringAvailable = canRename(ctxt.selectedElement.findRenamable())
                refactoring.isEnabled = refactoringAvailable
                refactoringPreview.isEnabled = refactoringAvailable
                refactoringSearchInComments.isEnabled = refactoringAvailable

                val tooltip = if (refactoringAvailable) null else "Select an element that can be renamed !"
                refactoring.toolTipText = tooltip
                refactoringPreview.toolTipText = tooltip
                refactoringSearchInComments.toolTipText = tooltip
            }
        }
    }

    private fun restoreAutomatic() {
        if (this.inputLanguageAutoPrefered && this.inputLanguage.selectedItem != Lang.AUTO) {
            setInputLanguage(Lang.AUTO)
            this.inputLanguage.font = this.inputLanguage.font.deriveFont(Font.PLAIN)
        }
    }

    private fun setInputLanguage(lang: Lang) {
        try {
            this.inputLanguageProgramaticSelection = true
            this.inputLanguage.selectedItem = lang
        } finally {
            this.inputLanguageProgramaticSelection = false
        }
    }

    private fun initPanel(): JPanel {

        val main = JBPanelWithEmptyText(GridLayoutManager(7, 4))
        main.border = JBUI.Borders.empty()
        main.withEmptyText("")
        main.add(
            JBLabel(
                """<html>
                Translating text :<br/>
                 &nbsp; ✓ <b>Select the “input” language or use “auto”</b><br/>
                 &nbsp; ✓ <b>Select the “output” language</b><br/>
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

        val languages = JBPanelWithEmptyText(GridLayoutManager(2, 3, Insets(0, 0, 0, 0), 5, 0))
        main.add(
            languages,
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
                0, 2, 1, 1,
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
            ActionButton(object : AnAction(ActionI18nIcons.SWAP) {
                override fun actionPerformed(e: AnActionEvent) {
                    swapLanguages()
                }
            }, Presentation("Swap Languages")
                .apply { icon = ActionI18nIcons.SWAP }, "s", Dimension(16, 16)
            ),
            GridConstraints(
                1, 1, 1, 1,
                ANCHOR_CENTER, FILL_NONE,
                SIZEPOLICY_FIXED, SIZEPOLICY_FIXED,
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

        val refactoringPanel = initRefactoringSetupPanel(refactoringModel)
        main.add(
            refactoringPanel,
            GridConstraints(
                6, 0, 1, 4,
                ANCHOR_WEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK,
                null, null, null
            )
        )

        panel.layout = BorderLayout(10, 30)
        panel.border = JBUI.Borders.empty(10)
        panel.withEmptyText("No literal selected yet found")
        panel.add(main, BorderLayout.CENTER)

        return panel
    }

    private fun initRefactoringSetupPanel(refactoringModel: RefactoringSetup): JBPanelWithEmptyText {

        refactoring.addActionListener {
            val selected = refactoring.isSelected
            refactoringPreview.isVisible = selected
            refactoringSearchInComments.isVisible = selected && !refactoringPreview.isSelected
            refactoringModel.useRefactoring = selected
            replaceAction.refresh()
            refactoringText.refresh()
        }

        refactoringPreview.addActionListener {
            val selected = refactoringPreview.isSelected
            refactoringModel.preview = selected
            refactoringSearchInComments.isVisible = refactoring.isSelected && !selected
            replaceAction.refresh()
        }

        refactoringSearchInComments.addActionListener {
            refactoringModel.searchInComments = refactoringSearchInComments.isSelected
        }

        refactoring.isSelected = refactoringModel.useRefactoring
        refactoringPreview.isSelected = refactoringModel.preview
        refactoringSearchInComments.isSelected = refactoringModel.searchInComments

        refactoringPreview.isVisible = refactoring.isSelected
        refactoringSearchInComments.isVisible = refactoring.isSelected && !refactoringPreview.isSelected

        val refactoringPanel = JBPanelWithEmptyText(GridLayoutManager(2, 3))
        refactoringPanel.add(refactoringText, GridConstraints(
            0, 0, 1, 3,
            ANCHOR_SOUTHWEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_SHRINK, SIZEPOLICY_CAN_SHRINK,null, null, null
        ))

        refactoringPanel.add(refactoring, GridConstraints(
            1, 0, 1, 1,
            ANCHOR_NORTHWEST, FILL_HORIZONTAL,
            SIZEPOLICY_FIXED,
            SIZEPOLICY_CAN_SHRINK,null, null, null
        ))
        refactoringPanel.add(refactoringPreview, GridConstraints(
            1, 1, 1, 1,
            ANCHOR_NORTHWEST, FILL_HORIZONTAL,
            SIZEPOLICY_FIXED,
            SIZEPOLICY_CAN_SHRINK,null, null, null
        ))
        refactoringPanel.add(refactoringSearchInComments, GridConstraints(
            1, 2, 1, 1,
            ANCHOR_NORTHWEST, FILL_HORIZONTAL,
            SIZEPOLICY_FIXED,
            SIZEPOLICY_CAN_SHRINK,null, null, null
        ))

        return refactoringPanel
    }

    override fun onTranslation(event: TranslationEvent) {

        translateAction.isEnabled = true
        replaceAction.isEnabled = ctxt.hasSelection()

        tTranslation.text = event.translation.translated.trimIndent()

        if (inputLanguage.lang == Lang.AUTO) {
            val model = inputLanguage.model as CollectionComboBoxModel<Lang>
            val find = model.items.find { it.code == event.translation.sourceLanguageIndentified }
            if (find != null) {
                setInputLanguage(find)
                this.inputLanguage.font = this.inputLanguage.font.deriveFont(Font.ITALIC)
            }
        }
    }

    override fun onUsagesCollected(origin: PsiElement?, usages: Set<PsiElement>) {
        refactoringText.refresh(usages, origin)
    }
}

private class TextArea : JBTextArea(15, 10) {
    init {
        lineWrap = true;
        wrapStyleWord = true;
        border = EmptyBorder(Insets(5, 5, 5, 5))
        font = JBFont.create(JBUI.Fonts.label().deriveFont(12))
    }
}

