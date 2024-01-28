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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import icons.ActionI18nIcons
import io.nimbly.i18n.util.*
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.swing.Icon

@Suppress("MissingActionUpdateThread")
class TranslateAction : AnAction() , DumbAware {

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

        val startOffset: Int
        val endOffset: Int
        val text: String?
        if (editor.selectionModel.hasSelection()) {
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
                .map { it.text!! }
                .reversed()
                .joinToString("\n")
            val text = joinToString

            val document = file.getDocument()
            if (document?.isWritable == true) {

                val indented = text.indentAs(document.getText(TextRange(startOffset, endOffset)))
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

            val flag = TranslationIcons.getFlag(output.trim().lowercase(), 0.6)
            val translationLines = translation
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

                    val renderer = TranslationHint(
                        text = translationLine,
                        flag = if (index == translationLines.size - 1) flag else null,
                        indent = if (index == translationLines.size - 1) xindent else 0
                    )

                    val p = InlayProperties().apply {
                        showAbove(true)
                        relatesToPrecedingText(false)
                        priority(1000)
                        disableSoftWrapping(false)
                    }

                    inlayModel.addBlockElement<HintRenderer>(startOffset, p, renderer)
                }
        }
    }

    override fun isDumbAware()
        = true
}

class TranslationHint(text: String, val flag: Icon?, val indent: Int) : HintRenderer(text) {

    val creationDate = LocalDateTime.now()

    override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
        r.x = indent - 6
        super.paint(inlay, g, r, textAttributes)
    }

    override fun useEditorFont(): Boolean {
        return true
    }

    override fun calcGutterIconRenderer(inlay: Inlay<*>): GutterIconRenderer? {
        if (flag != null)
            return TranslationIndicatorRenderer(inlay, flag)
        return null
    }
    fun sinceSeconds() = ChronoUnit.SECONDS.between(creationDate, LocalDateTime.now())

}

class TranslationIndicatorRenderer(inlay: Inlay<*>, val flag: Icon) : GutterIconRenderer() {

    override fun getIcon() = flag
    override fun getTooltipText() = "Click to apply translation"

    override fun hashCode() = icon.hashCode()
    override fun equals(other: Any?) = icon == (other as? TranslationIndicatorRenderer)?.icon
}

fun Editor.clearInlays(delay: Int = -1) {
    inlayModel.getBlockElementsInRange(0, document.textLength)
        .filter { it.renderer is TranslationHint }
        .filter { delay < 0 || (it.renderer as TranslationHint).sinceSeconds() > 5 }
        .forEach { Disposer.dispose(it) }
}