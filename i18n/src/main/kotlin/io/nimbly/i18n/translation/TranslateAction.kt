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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.RefactoringUiService
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.refactoring.util.CommonRefactoringUtil
import icons.ActionI18nIcons
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.Translation
import io.nimbly.i18n.translation.engines.TranslationEngineFactory
import io.nimbly.i18n.util.*

open class TranslateAction : DumbAwareAction()  {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        doUpdate(event, null)
    }

    fun doUpdate(event: AnActionEvent, editorRef: Editor?) {

        val editor = editorRef ?: event.editor
        event.presentation.isEnabledAndVisible = editor!=null

        val mySettings = TranslationPlusSettings.getSettings()
        val activeEngine = mySettings.activeEngine
        val engine = TranslationEngineFactory.engine(activeEngine)

        val output = TranslationPlusSettings.getSettings().output
        event.presentation.icon =  TranslationIcons.getFlag(output.trim().lowercase(), engine = engine)
            ?: ActionI18nIcons.I18N

        var readyToApplyTranslation = false
        editor?.let { ed ->
            readyToApplyTranslation = ed.inlayModel.getBlockElementsInRange(0, ed.document.textLength - 1)
                .filter { (it.renderer as? EditorHint)?.type == EHint.TRANSLATION }
                .isNotEmpty()
        }
        if (readyToApplyTranslation) {
            event.presentation.text = "Apply Translation"
        }
        else {
            event.presentation.text = "Translate"
        }

        super.update(event)
    }

    override fun actionPerformed(event: AnActionEvent) {
        doActionPerformed(event, null)
    }

    fun doActionPerformed(event: AnActionEvent, editorRef: Editor?) {

        val file = event.getData(CommonDataKeys.PSI_FILE)
        val editor = editorRef ?: event.editor
            ?: return
        val project = CommonDataKeys.PROJECT.getData(event.dataContext) ?: editor.project ?: file?.project
            ?: return

        val editorImpl = event.getData(CommonDataKeys.EDITOR) as? EditorImpl
        val isVCS = VcsDataKeys.COMMIT_MESSAGE_DOCUMENT.getData(event.dataContext) != null
        val caret = CommonDataKeys.CARET.getData(event.dataContext)?.offset

        doActionPerformed(project, editor, file, editorImpl, caret, isVCS)
    }

    fun doActionPerformed(
        project: Project,
        editor: Editor,
        file: PsiFile? = null,
        editorImpl: EditorImpl? = null,
        caret: Int? = null,
        isVCS: Boolean = false,
        withInlineTranslation: Boolean = true,
        forcedStyle: EStyle? = null)
    {

        val inlayModel = editor.inlayModel

        val zoom = editorImpl?.fontSize?.let { it / 13.0 } ?: 1.0

        val element: PsiElement?
        var startOffset: Int
        var endOffset: Int
        var text: String?
        val format: EFormat
        val style: EStyle
        val selectionEnd: Boolean
        if (editor.selectionModel.hasSelection()) {

            val offsetFirstNotEmpty = editor.selectionModel.findOffsetFirstNotNull()
            if (offsetFirstNotEmpty != editor.selectionModel.selectionStart) {
                editor.selectionModel.setSelection(offsetFirstNotEmpty, editor.selectionModel.selectionEnd)
            }

            val literal = editor.getLeafAtSelection()
            val isLiteralSelected = literal != null && literal.startOffset == editor.selectionModel.selectionStart && literal.endOffset == editor.selectionModel.selectionEnd
            element = if (isLiteralSelected) literal else null

            startOffset = editor.selectionModel.selectionStart
            endOffset = editor.selectionModel.selectionEnd
            text = editor.selectionModel.getSelectedText(false)
            format = editor.detectFormat()
            style = forcedStyle ?: text?.removeQuotes()?.detectStyle(isLiteralSelected) ?: EStyle.NORMAL

            selectionEnd = caret == endOffset
        }
        else if (isVCS) {

            element = null
            startOffset = 0
            endOffset = editor.document.textLength
            text = editor.document.text
            format = EFormat.TEXT
            style = forcedStyle ?: EStyle.NORMAL

            selectionEnd = caret == endOffset
        }
        else if (file != null && caret != null) {

            element = file.findElementAt(caret) ?: return
            startOffset = element.textRange.startOffset
            endOffset = element.textRange.endOffset

            // Do not select multiple line
            val lineStart = editor.document.getLineNumber(startOffset)
            val lineEnd = editor.document.getLineNumber(endOffset)
            if (lineStart != lineEnd) {

                val ln = editor.document.getLineNumber(caret)
                startOffset = editor.document.getLineStartOffset(ln) + editor.document.getLineTextStartOffset(caret)
                endOffset = editor.document.getLineEndOffset(ln)

                text = editor.document.getText(TextRange(startOffset, endOffset))
            }
            else {
                text = element.text ?: ""
            }

            if (caret == startOffset
                    && (text.isBlank() || text.replace("[\\p{L}\\p{N}\\p{M}]+".toRegex(), "").isNotBlank() && caret > 1)) {

                val ll = file.findElementAt(caret - 1) ?: return
                startOffset = ll.textRange.startOffset
                endOffset = ll.textRange.endOffset
                text = ll.text

                if (text.isBlank()) {
                    text = null
                }
            }

            format = file.detectFormat()
            style = forcedStyle ?: text?.removeQuotes()?.detectStyle(true) ?: EStyle.NORMAL

            selectionEnd = caret == endOffset

            editor.selectionModel.setSelection(startOffset, endOffset)
        }
        else {
            return
        }

        if (text == null)
            return

        val activeInlays = inlayModel.getBlockElementsInRange(startOffset, startOffset)
            .filter { (it.renderer as? EditorHint)?.type == EHint.TRANSLATION }

        if (activeInlays.isNotEmpty()) {

            //
            // Apply inlay's translation
            applyTranslation(activeInlays, editor, file, selectionEnd, startOffset, endOffset, format, project)
        }
        else {

            //
            // Translate
            val settings = TranslationPlusSettings.getSettings()
            val input = settings.input
            val output = settings.output

            val translation = TranslationManager.translate(output, input, text, format, style, Origin.from(element, editor), project)

            if (forcedStyle == null && translation?.translated?.isEmpty() == true) {

                // If the language is not latin at all, lets move to normal style otherwise translation is empty !
                return doActionPerformed(
                    project = project,
                    editor = editor,
                    file = file,
                    editorImpl = editorImpl,
                    caret = caret,
                    isVCS = isVCS,
                    withInlineTranslation = withInlineTranslation,
                    forcedStyle = EStyle.NORMAL)
            }

            translation
                ?: return

            EditorFactory.getInstance().clearInlays(editor.project)

            //
            // Translate and show inlay
            displayInlays(element, translation, editor, startOffset, zoom, !withInlineTranslation)

            //
            // Display inlays for references also
            if (file != null) {
                val targets = findUsages(CommonRefactoringUtil.getElementAtCaret(editor, file), editor)
                targets
                    .forEach {
                        if (element !=null) {
                            val editors = FileEditorManager.getInstance(project).getAllEditors(it.first.containingFile.virtualFile)
                            editors
                                .filterIsInstance<TextEditor>()
                                .map { it.editor }
                                .forEach { ed ->
                                    displayInlays(element, translation, ed, it.second, zoom, true, true)
                                }
                            }
                    }
            }
        }
    }

    private fun displayInlays(
        element: PsiElement?,
        translation: Translation,
        editor: Editor,
        startOffset: Int,
        zoom: Double,
        inputOnly: Boolean,
        secondaryIcon: Boolean = false
    ) {

        //
        // Input icon
        val inlayModel = editor.inlayModel
        val renderer = EditorHint(
            type = EHint.TRANSLATION,
            zoom = zoom,
            flag = translation.sourceLanguageIndentified.trim().lowercase(),
            translation = if (inputOnly) " " else "  ",
            element = element?.let { SmartPointerManager.getInstance(it.project).createSmartPsiElementPointer(it, element.containingFile) },
            secondaryIcon = secondaryIcon
        )
        val p = InlayProperties().apply {
            showAbove(false)
            relatesToPrecedingText(false)
            priority(1000)
            disableSoftWrapping(false)
        }
        inlayModel.addInlineElement<HintRenderer>(startOffset, p, renderer)
        if (inputOnly)
            return

        //
        // Output icon + translation
        val translationLines: MutableList<String> = translation.translated
            .split('\n')
            .reversed()
            .toMutableList()

        if (translationLines.size > 1 && translationLines.first().isBlank()) {
            translationLines[1] = translationLines[1] + '\n' + translationLines[0]
            translationLines.removeAt(0)
        }

        val output = TranslationPlusSettings.getSettings().output
        val xindent = editor.offsetToPoint2D(startOffset).x.toInt()

        translationLines
            .forEachIndexed { index, translationLine ->

                val ren = EditorHint(
                    type = EHint.TRANSLATION,
                    zoom = zoom,
                    translation = translationLine,
                    element = element?.let { SmartPointerManager.getInstance(it.project).createSmartPsiElementPointer(it, element.containingFile) },
                    flag = if (index == translationLines.size - 1) output.trim().lowercase() else null,
                    indent = if (index == translationLines.size - 1) xindent else 4
                )
                val ip = InlayProperties().apply {
                    showAbove(true)
                    relatesToPrecedingText(false)
                    priority(1000)
                    disableSoftWrapping(false)
                }

                inlayModel.addBlockElement<HintRenderer>(startOffset, ip, ren)
            }

        DaemonCodeAnalyzer.getInstance(editor.project).restart()
    }

    private fun applyTranslation(
        activeInlays: List<Inlay<*>>,
        editor: Editor,
        file: PsiFile?,
        selectionEnd: Boolean,
        startOffset: Int,
        endOffset: Int,
        format: EFormat,
        project: Project
    ) {
        val joinToString = activeInlays
            .map { it.renderer as EditorHint }
            .map { it.translation }
            .reversed()
            .map { it.trim().escapeFormat(format) }
            .joinToString("\n")

        val document = editor.document
        if (!document.isWritable) return

        val t = document.getText(TextRange(startOffset, endOffset))
        val indented = joinToString.indentAs(t)

        val refactoringSetup = RefactoringSetup()
        var doRename = false
        var elt = file?.findElementAt(startOffset + 1)
        if (refactoringSetup.useRefactoring && elt != null && elt.startOffset == startOffset && elt.endOffset == endOffset) {
            elt = elt.findRenamable()
            if (canRename(elt))
                doRename = true
        }
        elt ?: return

        if (doRename && refactoringSetup.useRefactoring && refactoringSetup.preview) {

            elt = RenamePsiElementProcessor.forElement(elt).substituteElementToRename(elt, editor)?.findRenamable()  ?: elt
            val d = RefactoringUiService.getInstance()
                .createRenameRefactoringDialog(project, elt, elt, editor)
            d.performRename(indented)
        }
        else if (doRename && refactoringSetup.useRefactoring && !refactoringSetup.preview) {

            elt = RenamePsiElementProcessor.forElement(elt).substituteElementToRename(elt, editor)?.findRenamable()  ?: elt
            val rename = RefactoringFactory.getInstance(project)
                .createRename(elt, indented, refactoringSetup.searchInComments, true)
            val usages = rename.findUsages()
            rename.doRefactoring(usages)
        }
        else {
            executeWriteCommand(project, "Translating with Translation+") {
                document.replaceString(startOffset, endOffset, indented)
            }
        }

        EditorFactory.getInstance().clearInlays(editor.project)
        if (selectionEnd)
            editor.selectionModel.removeSelection()
    }

    override fun isDumbAware()
        = true
}