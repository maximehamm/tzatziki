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

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import icons.ActionI18nIcons
import io.nimbly.i18n.util.*
import java.awt.Graphics
import java.awt.Rectangle
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Suppress("MissingActionUpdateThread")
open class TranslateAction : AnAction() , DumbAware {

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor!=null

        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")

        event.presentation.icon =  TranslationIcons.getFlag(output.trim().lowercase())
            ?: ActionI18nIcons.I18N

        var readyToApplyTranslation = false
        editor?.let {
            readyToApplyTranslation = it.inlayModel.getBlockElementsInRange(0, editor.document.textLength - 1)
                .filter { it.renderer is TranslationHint }
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

        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor =  CommonDataKeys.EDITOR.getData(event.dataContext) ?: return
        val inlayModel = editor.inlayModel

        val editorImpl = event.getData<Editor>(CommonDataKeys.EDITOR) as? EditorImpl
        val zoom = editorImpl?.fontSize?.let { it / 13.0 } ?: 1.0

        val startOffset: Int
        val endOffset: Int
        val text: String?
        if (editor.selectionModel.hasSelection()) {

            val offsetFirstNotEmpty = editor.selectionModel.findOffsetFirstNotNull()
            if (offsetFirstNotEmpty != editor.selectionModel.selectionStart) {
                editor.selectionModel.setSelection(offsetFirstNotEmpty, editor.selectionModel.selectionEnd)
            }

            startOffset = editor.selectionModel.selectionStart
            endOffset = editor.selectionModel.selectionEnd
            text = editor.selectionModel.getSelectedText(false)
        }
        else {
            val offset = CommonDataKeys.CARET.getData(event.dataContext)?.offset ?: return
            val l = file.findElementAt(offset) ?: return
            startOffset = l.textRange.startOffset
            endOffset = l.textRange.endOffset
            text = l.text

            editor.selectionModel.setSelection(startOffset, endOffset)
        }

        if (text == null)
            return

        val activeInlays = inlayModel.getBlockElementsInRange(startOffset, startOffset)
            .filter { it.renderer is TranslationHint }

        if (activeInlays.isNotEmpty()) {

            //
            // Apply inlay's translation
            //
            val joinToString = activeInlays
                .map { it.renderer as TranslationHint }
                .map { it.translation }
                .reversed()
                .joinToString("\n")

            val document = file.getDocument()
            if (document?.isWritable == true) {

                val indented = joinToString.indentAs(document.getText(TextRange(startOffset, endOffset)))
                executeWriteCommand(file.project, "Translating with Translation+", Runnable {
                    document.replaceString(startOffset, endOffset, indented)
                })
                editor.clearInlays()
            }

            return
        }
        else {

            //
            // Translate and show inlay
            //
            val input = PropertiesComponent.getInstance().getValue(SAVE_INPUT, "auto")
            val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")
            val translation = TranslationManager.translate(output, input, text)
                ?: return

            editor.clearInlays()

            val translationLines = translation.translated
                .split('\n')
                .reversed()
                .toMutableList()

            if (translationLines.size > 1 && translationLines.first().isBlank()) {
                translationLines[1] = translationLines[1] + '\n' + translationLines[0]
                translationLines.removeAt(0)
            }

            val xindent = editor.offsetToPoint2D(startOffset).x.toInt()
            val flag = TranslationIcons.getFlag(output.trim().lowercase(), 1.2)

            translationLines
                .forEachIndexed { index, translationLine ->

                    val renderer = TranslationHint(
                        zoom = zoom,
                        translation = translationLine,
                        flag = if (index == translationLines.size - 1) output.trim().lowercase() else null,
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

            val renderer = TranslationHint(
                zoom = zoom,
                flag = translation.sourceLanguageIndentified.trim().lowercase(),
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
    }

    override fun isDumbAware()
        = true
}

class TranslationHint(
    val zoom: Double,
    val translation: String = "",
    val flag: String?= null,
    val indent: Int? = null
) : HintRenderer(translation) {

    val creationDate = LocalDateTime.now()

    override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
        if (indent != null) {
            r.x = indent
            r.height += 4
            r.y += 2
        }

        if (flag != null) {

            val ratio = zoom * 0.8
            val spacing = (zoom * 5).toInt()

            val icon = TranslationIcons.getFlag(flag, ratio)!!

            val iconX = r.x
            val iconY = r.y + (r.height - icon.iconHeight) / 2 + 1

            // Draw the icon
            icon.paintIcon(null, g, iconX, iconY)

            // Call super.paint() to draw the text
            if (translation.isNotBlank()) {
                val modifiedR = Rectangle(r.x + icon.iconWidth + spacing, r.y, r.width, r.height)
                super.paint(inlay, g, modifiedR, textAttributes)
            }
            else {
                val modifiedR = Rectangle(r.x + icon.iconWidth + spacing, r.y, 0, r.height)
                super.paint(inlay, g, modifiedR, textAttributes)
            }
        }
        else {
            super.paint(inlay, g, r, textAttributes)
        }
    }

    override fun useEditorFont(): Boolean {
        return true
    }

    fun sinceSeconds() = ChronoUnit.SECONDS.between(creationDate, LocalDateTime.now())

}

fun Editor.clearInlays(delay: Int = -1) {
    inlayModel.getBlockElementsInRange(0, document.textLength)
        .filter { it.renderer is TranslationHint }
        .filter { delay < 0 || (it.renderer as TranslationHint).sinceSeconds() > 5 }
        .forEach { Disposer.dispose(it) }
    inlayModel.getInlineElementsInRange(0, document.textLength)
        .filter { it.renderer is TranslationHint }
        .filter { delay < 0 || (it.renderer as TranslationHint).sinceSeconds() > 5 }
        .forEach { Disposer.dispose(it) }
}