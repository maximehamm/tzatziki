/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

/**
 * Shared data types for the table decorator subsystem (geometry, hover state, drag state,
 * post-drop flash). Extracted from TzTableDecorator so the renderer / popup / hover helpers
 * can live in their own files without nesting under the decorator class.
 */

/** Layout of a single Gherkin table inside an editor — pipe X-positions and line range. */
internal data class TableGeometry(
    val firstLine: Int,
    val lastLine: Int,
    val pipeOffsets: List<Int>,
    val headerLine: Int?
)

/** Which border / region of a table's frame the cursor is over. */
internal enum class HoverZone { LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER, HEADER_SEPARATOR }

/** The pair (geometry, zone) describing the current hover hit. */
internal data class HoverState(val geometry: TableGeometry, val zone: HoverZone)

/** State captured between mousePressed and mouseReleased for a row/column drag gesture. */
internal data class DragState(
    val zone: HoverZone,
    val geom: TableGeometry,
    val sourceIndex: Int,
    val startX: Int,
    val startY: Int,
    var currentX: Int = startX,
    var currentY: Int = startY,
    var active: Boolean = false
)

/**
 * Brief highlight after a column drop or a shift action: solid orange segment over the
 * affected column (top border) or row (left border).
 *
 * Keyed by [tableFirstLine] rather than a full TableGeometry — after the move,
 * scheduleRefresh() rebuilds editorGeometries with fresh pipe offsets (column widths can
 * change), so a stored geometry would never match the freshly-painted one. The first line
 * is stable because TableEditOps only rewrites existing lines via replaceString().
 *
 * Exactly one of [columnIndex] / [rowLine] is set per flash.
 */
internal data class PostDropFlash(
    val tableFirstLine: Int,
    val columnIndex: Int? = null,
    val rowLine: Int? = null
)
