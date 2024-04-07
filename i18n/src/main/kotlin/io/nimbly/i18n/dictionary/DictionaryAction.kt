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

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import icons.ActionI18nIcons
import io.nimbly.i18n.util.*
import io.nimbly.i18n.util.EHint.DEFINITION

open class DictionaryAction : AnAction() , DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor!=null
        event.presentation.text = "Search Definition"
        super.update(event)
    }

    override fun actionPerformed(event: AnActionEvent) {

        val editor =  CommonDataKeys.EDITOR.getData(event.dataContext) ?: return
        val inlayModel = editor.inlayModel

        val editorImpl = event.getData(CommonDataKeys.EDITOR) as? EditorImpl
        val zoom = editorImpl?.fontSize?.let { it / 13.0 } ?: 1.0
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val caret = CommonDataKeys.CARET.getData(event.dataContext)?.offset

        var startOffset: Int
        var endOffset: Int
        var text: String?
        var camelCase = false
        val element: PsiElement?

        if (editor.selectionModel.hasSelection()) {

            element = null
            val offsetFirstNotEmpty = editor.selectionModel.findOffsetFirstNotNull()
            if (offsetFirstNotEmpty != editor.selectionModel.selectionStart) {
                editor.selectionModel.setSelection(offsetFirstNotEmpty, editor.selectionModel.selectionEnd)
            }

            startOffset = editor.selectionModel.selectionStart
            text = editor.selectionModel.getSelectedText(false)
        }
        else if (file != null && caret != null) {

            element = file.findElementAt(caret) ?: return
            startOffset = element.textRange.startOffset
            endOffset = element.textRange.endOffset
            text = element.text

            if (caret == startOffset
                    && (text.isBlank() || text.replace("[\\p{L}\\p{N}\\p{M}]+".toRegex(), "").isNotBlank() && caret > 1)) {

                val l = file.findElementAt(caret - 1) ?: return
                startOffset = l.textRange.startOffset
                endOffset = l.textRange.endOffset
                text = l.text

                if (text.isBlank()) {
                    text = null
                }
            }

            camelCase = text!=null && !text.contains(" ") && text.fromCamelCase() != text

            editor.selectionModel.setSelection(startOffset, endOffset)
        }
        else {
            return
        }

        if (text == null)
            return

        EditorFactory.getInstance().clearInlays(editor.project)

        //
        // Search definition
        //
        val def = DictionaryManager.searchDefinition(text, camelCase = camelCase)

        EditorFactory.getInstance().clearInlays(editor.project)

        val translationLines =
            if (def.status == EStatut.NOT_FOUND) {
                listOf("No definition found").toMutableList()
            }
            else {
                def.result!!.meanings
                    .map { m ->
                        m.definitions.map { d ->
                            d.definition.removeSuffix(".").removeStartParenthesis().trim().ifBlank { null }
                        }
                    }
                    .flatten()
                    .filterNotNull()
                    .toMutableList()
            }

        val startOfLine = editor.document.getLineTextStartOffset(startOffset)
        val xindent = editor.offsetToPoint2D(startOfLine).x.toInt()

        if (translationLines.size > 4) {
            translationLines.retainAll(translationLines.subList(0, 4))
            translationLines.add("...")
        }

        translationLines
            .reversed()
            .forEach { translationLine ->

                val renderer = EditorHint(
                    type = DEFINITION,
                    zoom = zoom,
                    translation = translationLine,
                    element = element?.let { SmartPointerManager.getInstance(it.project).createSmartPsiElementPointer(it, element.containingFile) },
                    icon = ActionI18nIcons.DICO,
                    indent = xindent
                )
                val p = InlayProperties().apply {
                    showAbove(true)
                    relatesToPrecedingText(false)
                    priority(1000)
                    disableSoftWrapping(false)
                }

                inlayModel.addBlockElement<HintRenderer>(startOffset, p, renderer)
            }

        val renderer = EditorHint(
            type = DEFINITION,
            zoom = zoom,
            icon = ActionI18nIcons.DICO,
            translation = " ",
            element = element?.let { SmartPointerManager.getInstance(it.project).createSmartPsiElementPointer(it, element.containingFile) },
            )
        val p = InlayProperties().apply {
            showAbove(false)
            relatesToPrecedingText(false)
            priority(1000)
            disableSoftWrapping(false)
        }
        inlayModel.addInlineElement<HintRenderer>(startOffset, p, renderer)
    }

    override fun isDumbAware() = true
}

private fun String.removeStartParenthesis(): String {
    if (!this.startsWith("("))
        return this
    val i = this.indexOf(")")
    if (i > 0 && i < this.length - 1)
        return this.substring(i+1).trim()
    return this
}
