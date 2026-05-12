/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import io.nimbly.tzatziki.util.TableEditOps
import java.awt.Point

/**
 * Pure geometry / hit-detection helpers for the table decorator.
 *
 * Kept stateless so they can be unit-tested and reused — none of these touch the
 * decorator's per-editor maps. The single entry point that takes a list of geometries
 * is [findHover].
 */

/**
 * Hit-test [point] against the frames in [geometries] and return the hovered zone, or
 * null when the cursor is not over any frame.
 *
 *  - TOP_BORDER activation extends up to ~12px above the table's first line, but stays
 *    short enough not to bleed onto a comment / blank line above. It is suppressed
 *    altogether for sub-tables that are the continuation of a logical Gherkin table
 *    split by an interleaved comment.
 *  - LEFT / RIGHT_BORDER only fire on the actual pipe rows — never on comment lines
 *    sitting between rows of the same logical table.
 *  - BOTTOM_BORDER and HEADER_SEPARATOR are intentionally not exposed; the top border
 *    is the single entry point for column actions and the header separator must stay
 *    a plain editor area.
 */
internal fun findHover(editor: Editor, geometries: List<TableGeometry>, point: Point): HoverState? {
    // Top zone now extends DOWNWARD into the upper half of the header row instead of
    // bleeding onto the text/comment line above — easier to aim and never conflicts
    // with editor content sitting just above the table.
    val tTopUp   = 2
    val tTopDown = editor.lineHeight / 4
    // Left/right edges: ~14px tolerance so the user has a comfortable hit zone for the
    // hand cursor and the drag/menu trigger.
    val tEdge    = 14
    val tRange   = tEdge

    val fm   = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
    val half = fm.charWidth('|') / 2

    for (geom in geometries) {
        if (geom.pipeOffsets.size < 2) continue
        val doc  = editor.document
        val firstX = editor.logicalPositionToXY(editor.offsetToLogicalPosition(geom.pipeOffsets.first())).x + half
        val lastX  = editor.logicalPositionToXY(editor.offsetToLogicalPosition(geom.pipeOffsets.last())).x + half
        val topY   = editor.logicalPositionToXY(editor.offsetToLogicalPosition(doc.getLineStartOffset(geom.firstLine))).y
        val bottomY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(doc.getLineStartOffset(geom.lastLine))).y + editor.lineHeight
        val mx = point.x; val my = point.y
        val inXRange = mx >= firstX - tRange && mx <= lastX + tRange
        val inYRange = my >= topY - tRange && my <= bottomY + tRange

        if (inXRange && my in topY - tTopUp .. topY + tTopDown &&
            !isContinuationOfLargerTable(editor, geom.firstLine)) {
            return HoverState(geom, HoverZone.TOP_BORDER)
        }
        if (inYRange && (mx in firstX - tEdge .. firstX + tEdge || mx in lastX - tEdge .. lastX + tEdge)) {
            if (isPipeLineAtY(editor, geom, my)) {
                return HoverState(
                    geom,
                    if (mx in firstX - tEdge .. firstX + tEdge) HoverZone.LEFT_BORDER else HoverZone.RIGHT_BORDER
                )
            }
        }
    }
    return null
}

/**
 * True when [firstLine] is the second (or later) half of a logical Gherkin table split
 * by an interleaved comment / blank line. The top edge of such a sub-table is inert
 * (no hand cursor, no tooltip, no popup, no drag start) so it never clashes with the
 * comment above. A doc-comment ABOVE a standalone table reads as a single contiguous
 * logical table starting at [firstLine] — top border stays active.
 */
internal fun isContinuationOfLargerTable(editor: Editor, firstLine: Int): Boolean {
    val tableLines = TableEditOps.collectTableLinesAround(editor, firstLine)
    return tableLines.isNotEmpty() && tableLines.first() < firstLine
}

/**
 * True when the editor line under pixel [y] (within [geom]'s span) is a real table row
 * (starts with `|`) — used to suppress the hand cursor over comment lines that may live
 * between rows.
 */
internal fun isPipeLineAtY(editor: Editor, geom: TableGeometry, y: Int): Boolean {
    val doc = editor.document
    val logicalLine = editor.xyToLogicalPosition(Point(0, y)).line
    if (logicalLine !in geom.firstLine..geom.lastLine) return false
    val start = doc.getLineStartOffset(logicalLine)
    val end   = doc.getLineEndOffset(logicalLine)
    return doc.charsSequence.subSequence(start, end).toString().trim().startsWith("|")
}

/** Map a Y pixel to a row index within [geom]'s pipe rows. */
internal fun computeRowIdxAt(editor: Editor, geom: TableGeometry, y: Int): Int {
    val doc = editor.document
    val tableLines = (geom.firstLine..geom.lastLine).filter { line ->
        doc.charsSequence
            .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
            .toString().trim().startsWith("|")
    }
    if (tableLines.isEmpty()) return 0
    return tableLines.indices.minBy { i ->
        val lineY = editor.logicalPositionToXY(
            editor.offsetToLogicalPosition(doc.getLineStartOffset(tableLines[i]))
        ).y
        Math.abs(y - (lineY + editor.lineHeight / 2))
    }
}

/** Map an X pixel to a column index inside [geom]. */
internal fun computeColIdxAt(editor: Editor, geom: TableGeometry, x: Int): Int {
    if (geom.pipeOffsets.size < 2) return 0
    val pipeXs = geom.pipeOffsets.map {
        editor.logicalPositionToXY(editor.offsetToLogicalPosition(it)).x
    }
    return pipeXs.zipWithNext().indices.minBy { i ->
        val mid = (pipeXs[i] + pipeXs[i + 1]) / 2
        Math.abs(x - mid)
    }
}
