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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import icons.ActionI18nIcons
import io.nimbly.i18n.util.*
import io.nimbly.i18n.util.EHint.DEFINITION

@Suppress("MissingActionUpdateThread")
open class DictionaryAction : AnAction() , DumbAware {

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor!=null
//        event.presentation.icon =  ActionI18nIcons.DICO
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
        var camelCase: Boolean = false
        var selectionEnd = false
        if (editor.selectionModel.hasSelection()) {

            val offsetFirstNotEmpty = editor.selectionModel.findOffsetFirstNotNull()
            if (offsetFirstNotEmpty != editor.selectionModel.selectionStart) {
                editor.selectionModel.setSelection(offsetFirstNotEmpty, editor.selectionModel.selectionEnd)
            }

            startOffset = editor.selectionModel.selectionStart
            endOffset = editor.selectionModel.selectionEnd
            text = editor.selectionModel.getSelectedText(false)

            selectionEnd = caret == endOffset
        }
        else if (file != null && caret != null) {

            val l = file.findElementAt(caret) ?: return
            startOffset = l.textRange.startOffset
            endOffset = l.textRange.endOffset
            text = l.text

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
            selectionEnd = caret == endOffset

            editor.selectionModel.setSelection(startOffset, endOffset)
        }
        else {
            return
        }

        if (text == null)
            return

        editor.clearInlays()

        //
        // Search definition
        //
        val def = DictionaryManager.searchDefinition(text, camelCase = camelCase)

        editor.clearInlays()

        val definitionText: String
        if (def.status == EStatut.NOT_FOUND) {
            definitionText = "No definition found"
        }
        else
            definitionText = def.result!!.meanings.first().definitions.first().definition

        val translationLines = definitionText
            .split('\n')
            .reversed()
            .toMutableList()

        if (translationLines.size > 1 && translationLines.first().isBlank()) {
            translationLines[1] = translationLines[1] + '\n' + translationLines[0]
            translationLines.removeAt(0)
        }

        val xindent = editor.offsetToPoint2D(startOffset).x.toInt()

        translationLines
            .forEachIndexed { index, translationLine ->

                val renderer = EditorHint(
                    type = DEFINITION,
                    zoom = zoom,
                    translation = translationLine,
                    icon = ActionI18nIcons.DICO,
                    indent = if (index == translationLines.size - 1) xindent else 4
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
            translation = "  "
        )
        val p = InlayProperties().apply {
            showAbove(false)
            relatesToPrecedingText(false)
            priority(1000)
            disableSoftWrapping(false)
        }
        inlayModel.addInlineElement<HintRenderer>(startOffset, p, renderer)
    }

    override fun isDumbAware()
        = true
}

