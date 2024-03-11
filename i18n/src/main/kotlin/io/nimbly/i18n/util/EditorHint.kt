package io.nimbly.i18n.util

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.Rectangle
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.swing.Icon

enum class EHint { TRANSLATION, DEFINITION }

class EditorHint(
    val type: EHint,
    val zoom: Double,
    val translation: String = "",
    val flag: String? = null,
    val icon: Icon? = null,
    val indent: Int? = null,
    val secondaryIcon: Boolean = false
) : HintRenderer(translation) {

    private val creationDate = LocalDateTime.now()
    private var focus: Boolean = false

    override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, attributes: TextAttributes) {

        val editor = inlay.editor
        if (editor !is EditorImpl) return

        if (indent != null) {
            r.x = indent
            r.height += 4
            r.y += 2
        }

        var att = getTextAttributes(editor) ?: attributes
        if (focus) {
            att = att.clone()
            att.foregroundColor =
                if (UIUtil.isUnderDarcula())
                    att.foregroundColor.brighter()
                else
                    att.foregroundColor.darker().darker()
        }

        if (flag != null || icon != null) {

            val ratio = zoom * 0.8
            val spacing = (zoom * 5).toInt()

            var icon =
                this.icon ?: TranslationIcons.getFlag(flag!!, ratio)!!

            if (secondaryIcon)
                icon = icon.addVeilToIcon()

            val iconX = r.x
            val iconY = r.y + (r.height - icon.iconHeight) / 2 + 1

            // Draw the icon
            icon.paintIcon(null, g, iconX, iconY)

            if (translation.isNotBlank()) {

                val modifiedR = Rectangle(r.x + icon.iconWidth + spacing, r.y, r.width, r.height)
                paintHint(g, editor, modifiedR, text, att, att, widthAdjustment, useEditorFont())
            }
            else {
                val modifiedR = Rectangle(r.x + icon.iconWidth + spacing, r.y, 0, r.height)
                super.paint(inlay, g, modifiedR, attributes)
            }
        }
        else {
            paintHint(g, editor, r, text, att, att, widthAdjustment, useEditorFont())
        }
    }

    override fun useEditorFont(): Boolean {
        return type == EHint.TRANSLATION
    }

    fun sinceSeconds() = ChronoUnit.SECONDS.between(creationDate, LocalDateTime.now())

    fun mouseEnter(): Boolean {
        val changed = this.focus == false
        focus = true
        return changed
    }

    fun mouseExit(): Boolean {
        val changed = this.focus == true
        focus = false
        return changed
    }
}