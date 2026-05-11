/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.plugins.cucumber.psi.GherkinHighlighter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D

/**
 * Read-only view of the decorator state needed to render a single table frame. Lets the
 * renderer live in its own file without coupling to TzTableDecorator's mutable maps.
 */
internal interface TableRenderState {
    fun hover(editor: Editor): HoverState?
    fun drag(editor: Editor): DragState?
    fun flash(editor: Editor): PostDropFlash?
    fun menuTarget(editor: Editor): MenuTargetIndicator?
    /** True when dropping [target] over [source]'s column would be a no-op (same column or
     *  right-neighbour insertion that collapses back). Drives the gray vs green colour. */
    fun isNoopColumnDrop(source: Int, target: Int): Boolean
    /** Same predicate for rows — drop on self or on the immediate next row collapses back. */
    fun isNoopRowDrop(source: Int, target: Int): Boolean
}

/**
 * Paints the rounded-corner frame around a Gherkin table — outer border, inner column
 * separators, header underline, hover overlay, drag-and-drop visuals, and the brief
 * post-drop / post-shift flash.
 *
 * Extracted from TzTableDecorator (was an inner class) so the decorator itself can stay
 * focused on lifecycle and routing.
 */
internal class TableFrameRenderer(
    private val editor: Editor,
    private val geometry: TableGeometry,
    private val tableStart: Int,
    private val tableEnd: Int,
    private val pipeOffsets: List<Int>,
    private val headerLineStart: Int?,
    private val state: TableRenderState
) : CustomHighlighterRenderer {

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (pipeOffsets.size < 2) return

        val hover       = state.hover(editor)
        val isHovered   = hover?.geometry == geometry
        val hoverZone   = if (isHovered) hover?.zone else null

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val fm        = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        val pipeWidth = fm.charWidth('|')
        val half      = pipeWidth / 2
        val base      = pipeColor(editor)
        val thinColor = Color(base.red, base.green, base.blue, (base.alpha * 0.55).toInt().coerceAtLeast(40))
        val bg        = editor.colorsScheme.defaultBackground
        val borderColor = base.lightenTowards(bg, 0.35f)

        val pipeXs  = pipeOffsets.map { editor.logicalPositionToXY(editor.offsetToLogicalPosition(it)).x + half }
        val firstX  = pipeXs.first()
        val lastX   = pipeXs.last()
        val topY    = editor.logicalPositionToXY(editor.offsetToLogicalPosition(tableStart)).y
        val bottomY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(tableEnd)).y + editor.lineHeight
        val arc = 5f

        // 1. Mask pipe characters
        g2.color = bg
        for (x in pipeXs) g2.fillRect(x - half, topY, pipeWidth, bottomY - topY)

        // 2. Inner vertical separators
        g2.stroke = BasicStroke(0.8f)
        g2.color  = thinColor
        for (x in pipeXs.drop(1).dropLast(1)) g2.drawLine(x, topY, x, bottomY)

        // 3. Header separator — thicker on hover
        if (headerLineStart != null) {
            val hy = editor.logicalPositionToXY(editor.offsetToLogicalPosition(headerLineStart)).y + editor.lineHeight - 1
            g2.stroke = if (hoverZone == HoverZone.HEADER_SEPARATOR) BasicStroke(2.0f) else BasicStroke(0.8f)
            g2.color  = if (hoverZone == HoverZone.HEADER_SEPARATOR) borderColor else thinColor
            g2.drawLine(firstX, hy, lastX, hy)
        }

        // 4. Outer rounded rectangle (thin always)
        g2.stroke = BasicStroke(1.0f)
        g2.color  = borderColor
        g2.draw(RoundRectangle2D.Float(
            firstX.toFloat(), topY.toFloat(),
            (lastX - firstX).toFloat(), (bottomY - topY).toFloat(),
            arc, arc
        ))

        // 5. Hovered segment overlay (thick, on top of the thin rect).
        //    Skip it when a drag is in progress on this table — the drag visuals below
        //    take over the affected border entirely.
        val drag = state.drag(editor)
        val isColumnDrag = drag?.active == true &&
                           drag.geom == geometry &&
                           drag.zone == HoverZone.TOP_BORDER
        val isRowDrag    = drag?.active == true &&
                           drag.geom == geometry &&
                           drag.zone == HoverZone.LEFT_BORDER
        val halfArc = (arc / 2).toInt()
        if (!isColumnDrag && !isRowDrag) {
            when (hoverZone) {
                HoverZone.TOP_BORDER    -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(firstX + halfArc, topY, lastX - halfArc, topY)
                }
                HoverZone.BOTTOM_BORDER -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(firstX + halfArc, bottomY, lastX - halfArc, bottomY)
                }
                HoverZone.LEFT_BORDER   -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(firstX, topY + halfArc, firstX, bottomY - halfArc)
                }
                HoverZone.RIGHT_BORDER  -> {
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.drawLine(lastX, topY + halfArc, lastX, bottomY - halfArc)
                }
                else -> {}
            }
        }

        // 6. Column drag-and-drop visuals (top border only — row DnD comes later).
        if (isColumnDrag && pipeXs.size >= 2 && drag!!.sourceIndex in 0 until pipeXs.size - 1) {
            val accent = Color(0x00, 0xA9, 0x17)            // Cucumber+ green
            val sourceColor = Color(0xE5, 0x71, 0x00)       // Contrasting orange = "lifted from here"

            // (a) Source column — strong dashed orange line on the top border so the user
            //     keeps track of where the dragged column came from.
            val srcX1 = pipeXs[drag.sourceIndex]
            val srcX2 = pipeXs[drag.sourceIndex + 1]
            g2.stroke = BasicStroke(
                3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                1f, floatArrayOf(6f, 4f), 0f
            )
            g2.color = sourceColor
            g2.drawLine(srcX1 + halfArc, topY, srcX2 - halfArc, topY)

            // Compute the closest column slot under the cursor (drop target).
            val targetIdx = pipeXs.zipWithNext().indices.minBy { i ->
                val mid = (pipeXs[i] + pipeXs[i + 1]) / 2
                Math.abs(drag.currentX - mid)
            }

            // No-op zone (drop on source or on right neighbor) → use gray for target+cursor
            // so the user understands the drop won't move anything.
            val dropNoop = state.isNoopColumnDrop(drag.sourceIndex, targetIdx)
            val activeColor = if (dropNoop) Color(0x9E, 0x9E, 0x9E) else accent

            // (b) Drop target — thick segment over the column where the cursor sits.
            val tgtX1 = pipeXs[targetIdx]
            val tgtX2 = pipeXs[targetIdx + 1]
            g2.stroke = BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.color = activeColor
            g2.drawLine(tgtX1 + halfArc, topY, tgtX2 - halfArc, topY)

            // (c) Floating segment under the cursor — width matches the source column,
            //     centered on currentX (clamped to the frame).
            val segWidth = srcX2 - srcX1
            val cx = drag.currentX.coerceIn(firstX + segWidth / 2, lastX - segWidth / 2)
            g2.stroke = BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.color = activeColor
            g2.drawLine(cx - segWidth / 2 + halfArc, topY, cx + segWidth / 2 - halfArc, topY)
        }

        // 6b. Row drag-and-drop visuals (left border).
        if (isRowDrag) {
            val doc = editor.document
            val pipeLines = (geometry.firstLine..geometry.lastLine).filter { line ->
                doc.charsSequence
                    .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
                    .toString().trim().startsWith("|")
            }
            if (pipeLines.size >= 2 && drag!!.sourceIndex in pipeLines.indices) {
                val rowYs = pipeLines.map { l ->
                    editor.logicalPositionToXY(editor.offsetToLogicalPosition(doc.getLineStartOffset(l))).y
                }
                val lineH = editor.lineHeight
                val accent      = Color(0x00, 0xA9, 0x17)            // Cucumber+ green
                val sourceColor = Color(0xE5, 0x71, 0x00)            // Same orange as column source

                // Vertical segments are visually thinner than horizontal ones of the same
                // stroke width — bump the row stroke so the visuals read as cleanly as the
                // column ones.
                val rowStroke = BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

                // (a) Source row — solid orange vertical segment on the left border.
                //     We don't dash it like the column source: the segment height (~1 line)
                //     is too short to make dashes legible.
                val srcY1 = rowYs[drag.sourceIndex]
                val srcY2 = srcY1 + lineH
                g2.stroke = rowStroke
                g2.color = sourceColor
                g2.drawLine(firstX, srcY1 + halfArc, firstX, srcY2 - halfArc)

                // Closest row under the cursor (drop target).
                val targetIdx = rowYs.indices.minBy { i ->
                    val mid = rowYs[i] + lineH / 2
                    Math.abs(drag.currentY - mid)
                }

                // No-op zone (drop on source or on its bottom neighbour) → gray.
                val dropNoop = state.isNoopRowDrop(drag.sourceIndex, targetIdx)
                val activeColor = if (dropNoop) Color(0x9E, 0x9E, 0x9E) else accent

                // (b) Drop target — thick segment over the row where the cursor sits.
                val tgtY1 = rowYs[targetIdx]
                val tgtY2 = tgtY1 + lineH
                g2.stroke = rowStroke
                g2.color = activeColor
                g2.drawLine(firstX, tgtY1 + halfArc, firstX, tgtY2 - halfArc)

                // (c) Floating segment under the cursor — height matches the source row,
                //     centered on currentY (clamped to the frame).
                val segH   = lineH
                val firstY = rowYs.first()
                val lastY  = rowYs.last() + lineH
                val cy = drag.currentY.coerceIn(firstY + segH / 2, lastY - segH / 2)
                g2.stroke = rowStroke
                g2.color = activeColor
                g2.drawLine(firstX, cy - segH / 2 + halfArc, firstX, cy + segH / 2 - halfArc)
            }
        }

        // 7. Post-drop flash — brief solid orange segment over the column / row that just
        //    moved (drag-and-drop, or shift-left / shift-right / shift-up / shift-down).
        //    Cleared by the Swing Timer in TzTableDecorator.postFlash().
        //
        //    Matching strategy:
        //      - Column flash → must match this geometry's firstLine, since column indices
        //        differ between sub-tables (a logical table split by a `# comment` separator
        //        renders as multiple geometries with their own pipe layouts).
        //      - Row flash    → matched by the row's document line being inside this
        //        geometry's range. That's enough to disambiguate across sub-tables.
        val flash = state.flash(editor)
        if (flash != null) {
            val sourceColor = Color(0xE5, 0x71, 0x00)   // Same orange as the source ghost.

            // Column flash → horizontal segment on the top border.
            val flashCol = flash.columnIndex
            if (flashCol != null &&
                flash.tableFirstLine == geometry.firstLine &&
                pipeXs.size >= 2 && flashCol in 0 until pipeXs.size - 1) {
                g2.stroke = BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.color = sourceColor
                val fx1 = pipeXs[flashCol]
                val fx2 = pipeXs[flashCol + 1]
                g2.drawLine(fx1 + halfArc, topY, fx2 - halfArc, topY)
            }

            // Row flash → vertical segment on the left border at the row's Y position.
            // Vertical strokes need to be thicker than horizontal ones to read the same way.
            val flashRow = flash.rowLine
            if (flashRow != null && flashRow in geometry.firstLine..geometry.lastLine) {
                val rowY = editor.logicalPositionToXY(
                    editor.offsetToLogicalPosition(editor.document.getLineStartOffset(flashRow))
                ).y
                g2.stroke = BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.color = sourceColor
                g2.drawLine(firstX, rowY + halfArc, firstX, rowY + editor.lineHeight - halfArc)
            }
        }

        // 8. Menu-target indicator — persistent orange segment shown while the table popup
        //    menu is open, so the user sees which row / column the menu acts on. Dashed
        //    for columns, solid for rows (matches the DnD source visual language).
        val menu = state.menuTarget(editor)
        if (menu != null) {
            val sourceColor = Color(0xE5, 0x71, 0x00)

            val menuCol = menu.columnIndex
            if (menuCol != null &&
                menu.tableFirstLine == geometry.firstLine &&
                pipeXs.size >= 2 && menuCol in 0 until pipeXs.size - 1) {
                val mx1 = pipeXs[menuCol]
                val mx2 = pipeXs[menuCol + 1]
                g2.stroke = BasicStroke(
                    3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    1f, floatArrayOf(6f, 4f), 0f
                )
                g2.color = sourceColor
                g2.drawLine(mx1 + halfArc, topY, mx2 - halfArc, topY)
            }

            val menuRow = menu.rowLine
            if (menuRow != null && menuRow in geometry.firstLine..geometry.lastLine) {
                val rowY = editor.logicalPositionToXY(
                    editor.offsetToLogicalPosition(editor.document.getLineStartOffset(menuRow))
                ).y
                g2.stroke = BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.color = sourceColor
                g2.drawLine(firstX, rowY + halfArc, firstX, rowY + editor.lineHeight - halfArc)
            }
        }
    }

    private fun pipeColor(editor: Editor): Color =
        editor.colorsScheme.getAttributes(GherkinHighlighter.PIPE)?.foregroundColor
            ?: editor.colorsScheme.defaultForeground

    private fun Color.lightenTowards(target: Color, factor: Float) = Color(
        (red   + (target.red   - red)   * factor).toInt().coerceIn(0, 255),
        (green + (target.green - green) * factor).toInt().coerceIn(0, 255),
        (blue  + (target.blue  - blue)  * factor).toInt().coerceIn(0, 255),
        alpha
    )
}
