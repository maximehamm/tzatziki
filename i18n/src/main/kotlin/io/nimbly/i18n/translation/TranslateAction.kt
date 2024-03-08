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

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.RefactoringUiService
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import icons.ActionI18nIcons
import io.nimbly.i18n.util.*

@Suppress("MissingActionUpdateThread")
open class TranslateAction : DumbAwareAction()  {

    override fun update(event: AnActionEvent) {
        doUpdate(event, null)
    }

    fun doUpdate(event: AnActionEvent, editorRef: Editor?) {

        val editor = editorRef ?: event.editor
        event.presentation.isEnabledAndVisible = editor!=null

        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")

        event.presentation.icon =  TranslationIcons.getFlag(output.trim().lowercase())
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
        file: PsiFile?,
        editorImpl: EditorImpl?,
        caret: Int?,
        isVCS: Boolean,
        withInlineTranslation: Boolean = true)
    {

        val inlayModel = editor.inlayModel

        val zoom = editorImpl?.fontSize?.let { it / 13.0 } ?: 1.0

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

            startOffset = editor.selectionModel.selectionStart
            endOffset = editor.selectionModel.selectionEnd
            text = editor.selectionModel.getSelectedText(false)
            format = editor.detectFormat()
            style = text?.removeQuotes()?.detectStyle(false) ?: EStyle.NORMAL

            selectionEnd = caret == endOffset
        }
        else if (isVCS) {

            startOffset = 0
            endOffset = editor.document.textLength
            text = editor.document.text
            format = EFormat.TEXT
            style = EStyle.NORMAL

            selectionEnd = caret == endOffset
        }
        else if (file != null && caret != null) {

            val l = file.findElementAt(caret) ?: return
            startOffset = l.textRange.startOffset
            endOffset = l.textRange.endOffset

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
                text = l.text ?: ""
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
            style = text?.removeQuotes()?.detectStyle(true) ?: EStyle.NORMAL

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
            val input = PropertiesComponent.getInstance().getValue(SAVE_INPUT, Lang.AUTO.code)
            val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, Lang.DEFAULT.code)

            val translation = TranslationManager.translate(output, input, text, format, style, project)
                ?: return

            EditorFactory.getInstance().clearInlays()

            //
            // Translate and show inlay
            displayInlays(translation, editor, startOffset, zoom, !withInlineTranslation)

            //
            // Display inlays for references also
            val refactoringSetup = RefactoringSetup()
            if (file!=null && refactoringSetup.useRefactoring) {

                var elt = file.findElementAt(startOffset).findRenamable()
                if (elt != null && canRename(elt)) {

                    elt = RenamePsiElementProcessor.forElement(elt).substituteElementToRename(elt, editor)?.findRenamable()  ?: elt

                    val projectScope = GlobalSearchScope.allScope(project)
                    val rename = RefactoringFactory.getInstance(project)
                        .createRename(elt, text, projectScope, refactoringSetup.searchInComments, true)
                    val usages = rename.findUsages()

                    val allRenames = mutableMapOf<PsiElement, String>()
                    allRenames[elt] = translation.translated

                    val processors = RenamePsiElementProcessor.allForElement(elt)
                    for (processor in processors) {
                        if (processor.canProcessElement(elt)) {
                            processor.prepareRenaming(elt, translation.translated, allRenames)
                        }
                    }

                    val targets = mutableSetOf<Int>()
                    allRenames.forEach { (resolved, renamed) ->
                        if (resolved is PsiNameIdentifierOwner) {
                            val o = resolved.identifyingElement?.startOffset
                            if (o != null && o != startOffset)
                                targets.add(o)
                        }
                    }

                    usages.forEach { ref ->
                        val r = ref.reference?.element // ?.findRenamable()
                        if (r != null && r.containingFile == file) {
                            PsiTreeUtil.collectElements(r) {
                                if (it is PsiReference && it.resolve() == elt)
                                    targets.add(it.startOffset + it.rangeInElement.startOffset)
                                false
                            }
                        }
                    }

                    targets.remove(startOffset)
                    targets.forEach {
                        displayInlays(translation, editor, it, zoom, true)
                    }
                }
            }
        }
    }

    private fun displayInlays(
        translation: GTranslation,
        editor: Editor,
        startOffset: Int,
        zoom: Double,
        inputOnly: Boolean
    ) {

        //
        // Input icon
        val inlayModel = editor.inlayModel
        val renderer = EditorHint(
            type = EHint.TRANSLATION,
            zoom = zoom,
            flag = translation.sourceLanguageIndentified.trim().lowercase(),
            translation = if (inputOnly) " " else "  "
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

        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, Lang.DEFAULT.code)
        val xindent = editor.offsetToPoint2D(startOffset).x.toInt()

        translationLines
            .forEachIndexed { index, translationLine ->

                val ren = EditorHint(
                    type = EHint.TRANSLATION,
                    zoom = zoom,
                    translation = translationLine,
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
        var elt = file?.findElementAt(startOffset)
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
                .createRename(elt!!, indented, refactoringSetup.searchInComments, true)
            val usages = rename.findUsages()
            rename.doRefactoring(usages)
        }
        else {
            executeWriteCommand(project, "Translating with Translation+") {
                document.replaceString(startOffset, endOffset, indented)
            }
        }

        EditorFactory.getInstance().clearInlays()
        if (selectionEnd)
            editor.selectionModel.removeSelection()
    }

    override fun isDumbAware()
        = true
}