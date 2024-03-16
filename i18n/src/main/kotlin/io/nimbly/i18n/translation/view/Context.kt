package io.nimbly.i18n.translation.view

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import io.nimbly.i18n.translation.TranslateAction
import io.nimbly.i18n.util.*
import java.awt.Cursor

class Context {

    var editor: Editor? = null
        set(editor) {
            initListener(editor)
            field = editor
        }

    var document: Document? = null
    var selectedElement: PsiElement?= null
    var startOffset: Int? = null
    var endOffset: Int? = null
    var format: EFormat = EFormat.TEXT
    var style: EStyle = EStyle.NORMAL

    fun hasSelection(): Boolean {
        val s = this.startOffset
        val e = this.endOffset
        if (s == null || e == null)
            return false
        return e - s > 0
    }

    val project get() = editor?.project

    private var mouseListener: TranslationMouseMotionListener? = null

    private fun initListener(editor: Editor?) {

        if (this.editor != editor) {

            if (mouseListener != null) {
                this.editor?.removeEditorMouseMotionListener(mouseListener!!)
                mouseListener = null
            }

            if (editor != null) {
                mouseListener = TranslationMouseMotionListener()
                editor.addEditorMouseMotionListener(mouseListener!!)
                editor.addEditorMouseListener(mouseListener!!)
            }
        }


    }

    class TranslationMouseMotionListener : EditorMouseMotionListener, EditorMouseListener {

        override fun mouseMoved(e: EditorMouseEvent) {

            val focusInlays = findTranslationInlays(e, true)
            val maxLength = focusInlays.maxOfOrNull { it.renderer.translation.length }

            if (focusInlays.isNotEmpty()
                && focusInlays.first().visualPosition.column < e.visualPosition.column
                && focusInlays.first().visualPosition.column + maxLength!! + 2 > e.visualPosition.column) {
                val customCursor = Cursor(Cursor.HAND_CURSOR)
                e.editor.contentComponent.cursor = customCursor
            }
            else {
                e.editor.contentComponent.cursor = Cursor(Cursor.DEFAULT_CURSOR)
            }

            val toReplace = mutableListOf<Inlay<EditorHint>>()
            e.editor.getTranslationInlays().forEach { inlay ->
                if (focusInlays.find { it.renderer == inlay.renderer } != null) {
                    if (inlay.renderer.mouseEnter())
                        toReplace.add(inlay)
                } else {
                    if (inlay.renderer.mouseExit())
                        toReplace.add(inlay)
                }
            }

            if (toReplace.isEmpty())
                return

            toReplace.forEach {
                Disposer.dispose(it)
            }

            val ip = InlayProperties().apply {
                showAbove(true)
                relatesToPrecedingText(false)
                priority(1000)
                disableSoftWrapping(false)
            }
            toReplace.forEach {
                e.editor.inlayModel.addBlockElement<HintRenderer>(it.offset, ip, it.renderer)
            }
        }

        override fun mousePressed(e: EditorMouseEvent) {

            findTranslationInlays(e)
                .firstOrNull()
                ?: return

            val elt = e.editor.file?.findElementAt(e.offset)
                ?: return

            TranslateAction().doActionPerformed(
                project = elt.project,
                editor = e.editor,
                file = elt.containingFile)
        }

        private fun findTranslationInlays(e: EditorMouseEvent, fullLine: Boolean = false): List<Inlay<EditorHint>> {

            val p1 = e.mouseEvent.point
            val p2 = e.editor.visualPositionToXY(e.visualPosition)
            val aboveLine = (p2.y - p1.y >= 0)

            val focusInlay = if (!aboveLine) emptyList() else
                e.editor.inlayModel.getBlockElementsForVisualLine(e.visualPosition.line, true)
                    .filter { (it.renderer as? EditorHint)?.translation?.isNotBlank() == true }
                    //.filter { fullLine || it.visualPosition.column < e.visualPosition.column }
                    //.filter { fullLine || it.visualPosition.column + (it.renderer as EditorHint).translation.length + 2 > e.visualPosition.column }
                    as List<Inlay<EditorHint>>
            return focusInlay
        }
    }
}
