/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package io.nimbly.tzatziki.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import io.nimbly.tzatziki.psi.format

/**
 * Document-level row/column edits on Gherkin tables that preserve interleaved
 * comments, blank lines, and any other non-row content within the table region.
 *
 * Only lines starting with `|` are touched; everything else is left intact.
 * After the edit, every distinct GherkinTable PSI element found in the affected
 * region is reformatted (column alignment).
 */
object TableEditOps {

    sealed class Op(val title: String) {
        class InsertRow(val atIndex: Int, val above: Boolean)
            : Op(if (above) "Add row above" else "Add row below")
        class DeleteRow(val atIndex: Int)
            : Op("Delete row")
        class InsertColumn(val atIndex: Int, val before: Boolean)
            : Op(if (before) "Add column to left" else "Add column to right")
        class DeleteColumn(val atIndex: Int)
            : Op("Delete column")
        class ShiftRow(val atIndex: Int, val delta: Int)
            : Op(if (delta < 0) "Shift row up" else "Shift row down")
        class ShiftColumn(val atIndex: Int, val delta: Int)
            : Op(if (delta < 0) "Shift column left" else "Shift column right")
    }

    fun apply(editor: Editor, tableLines: List<Int>, op: Op) {
        val project = editor.project ?: return
        val doc = editor.document
        if (tableLines.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project, op.title, null, {
            // Column operations must affect the WHOLE logical table — including rows
            // that are separated from the caller's fragment by comments or blank lines.
            // Row operations only affect the caller's fragment (the user's intent is
            // local to where the popup was opened).
            val effectiveLines: List<Int> = when (op) {
                is Op.InsertColumn, is Op.DeleteColumn -> {
                    // Expand outward from the fragment's first row to cover the whole
                    // logical table that may span interleaved comments / blank lines.
                    collectTableLinesAround(editor, tableLines.first())
                        .ifEmpty { tableLines }
                }
                else -> tableLines
            }

            // Snapshot the original text of each row line. Line numbers stay stable
            // until we insert/delete an entire line; cell-level edits only change
            // content within a line.
            val rows: List<String> = effectiveLines.map { line ->
                doc.text.substring(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
            }
            @Suppress("NAME_SHADOWING") val tableLines = effectiveLines

            when (op) {
                is Op.InsertRow -> {
                    val refIdx = op.atIndex.coerceIn(0, rows.size - 1)
                    val refRow = rows[refIdx]
                    val cellCount = parseRowCells(refRow).size.coerceAtLeast(1)
                    val indent = refRow.takeWhile { it == ' ' || it == '\t' }
                    val newRow = buildEmptyRow(indent, cellCount)
                    val targetLine = if (op.above) tableLines[refIdx] else tableLines[refIdx] + 1
                    val insertOffset = if (targetLine < doc.lineCount)
                        doc.getLineStartOffset(targetLine)
                    else
                        doc.textLength
                    doc.insertString(insertOffset, newRow + "\n")
                }
                is Op.DeleteRow -> {
                    if (rows.size <= 1) return@runWriteCommandAction
                    if (op.atIndex !in rows.indices) return@runWriteCommandAction
                    val line = tableLines[op.atIndex]
                    val start = doc.getLineStartOffset(line)
                    val end = if (line + 1 < doc.lineCount)
                        doc.getLineStartOffset(line + 1)
                    else
                        doc.getLineEndOffset(line)
                    doc.deleteString(start, end)
                }
                is Op.InsertColumn -> {
                    // Iterate from bottom to top so earlier offsets stay valid as we replace lines
                    tableLines.zip(rows).reversed().forEach { (line, original) ->
                        val cells = parseRowCells(original).toMutableList()
                        val safeAt = (if (op.before) op.atIndex else op.atIndex + 1)
                            .coerceIn(0, cells.size)
                        cells.add(safeAt, "")
                        val newLine = buildRowLine(cells, original)
                        doc.replaceString(doc.getLineStartOffset(line), doc.getLineEndOffset(line), newLine)
                    }
                }
                is Op.DeleteColumn -> {
                    tableLines.zip(rows).reversed().forEach { (line, original) ->
                        val cells = parseRowCells(original).toMutableList()
                        if (cells.size <= 1 || op.atIndex !in cells.indices) return@forEach
                        cells.removeAt(op.atIndex)
                        val newLine = buildRowLine(cells, original)
                        doc.replaceString(doc.getLineStartOffset(line), doc.getLineEndOffset(line), newLine)
                    }
                }
                is Op.ShiftRow -> {
                    val from = op.atIndex
                    val to = from + op.delta
                    if (from !in rows.indices || to !in rows.indices || from == to) return@runWriteCommandAction
                    // Swap row CONTENT only — keep line positions and surrounding non-row lines
                    // (comments, blanks) untouched. We rewrite both row lines.
                    val origIndent = rows[from].takeWhile { it == ' ' || it == '\t' }
                    val targetIndent = rows[to].takeWhile { it == ' ' || it == '\t' }
                    val fromCells = parseRowCells(rows[from])
                    val toCells = parseRowCells(rows[to])
                    val newAtFrom = buildRowLine(toCells, rows[from])
                    val newAtTo = buildRowLine(fromCells, rows[to])
                    // Replace the lines independently (replaceString doesn't shift line numbers)
                    doc.replaceString(
                        doc.getLineStartOffset(tableLines[from]),
                        doc.getLineEndOffset(tableLines[from]),
                        newAtFrom
                    )
                    doc.replaceString(
                        doc.getLineStartOffset(tableLines[to]),
                        doc.getLineEndOffset(tableLines[to]),
                        newAtTo
                    )
                    // origIndent/targetIndent unused but signal intent: indents are preserved by
                    // buildRowLine via the original-line argument.
                    @Suppress("UNUSED_VARIABLE") val unusedA = origIndent
                    @Suppress("UNUSED_VARIABLE") val unusedB = targetIndent
                }
                is Op.ShiftColumn -> {
                    val from = op.atIndex
                    // Swap two columns in every `|` row, leaving non-row lines alone.
                    tableLines.zip(rows).reversed().forEach { (line, original) ->
                        val cells = parseRowCells(original).toMutableList()
                        val to = from + op.delta
                        if (from !in cells.indices || to !in cells.indices || from == to) return@forEach
                        val tmp = cells[from]; cells[from] = cells[to]; cells[to] = tmp
                        val newLine = buildRowLine(cells, original)
                        doc.replaceString(doc.getLineStartOffset(line), doc.getLineEndOffset(line), newLine)
                    }
                }
            }

            // Re-format each distinct GherkinTable in the region (handles cases where
            // comments split the visual region into multiple PSI tables).
            PsiDocumentManager.getInstance(project).commitDocument(doc)
            val seen = HashSet<Int>()
            tableLines.forEach { line ->
                val safeLine = line.coerceAtMost(doc.lineCount - 1).coerceAtLeast(0)
                val table = editor.findTableAt(doc.getLineStartOffset(safeLine)) ?: return@forEach
                val key = System.identityHashCode(table)
                if (seen.add(key)) table.format()
            }
        })
    }

    /**
     * Collects all line numbers in the document that begin with `|`, expanding outward
     * from [seedLine] across interleaved comments and blank lines as long as a `|`-row
     * is reachable. Useful for tests and any caller that doesn't already have the
     * popup geometry's pre-filtered list.
     */
    fun collectTableLinesAround(editor: Editor, seedLine: Int): List<Int> {
        val doc = editor.document
        if (seedLine !in 0 until doc.lineCount) return emptyList()
        if (!isPipeRow(doc, seedLine)) return emptyList()

        var top = seedLine
        while (top > 0 && (isPipeRow(doc, top - 1) || isCommentOrBlank(doc, top - 1))) top--
        // Trim back to first pipe row
        while (top < seedLine && !isPipeRow(doc, top)) top++

        var bot = seedLine
        while (bot < doc.lineCount - 1 && (isPipeRow(doc, bot + 1) || isCommentOrBlank(doc, bot + 1))) bot++
        while (bot > seedLine && !isPipeRow(doc, bot)) bot--

        return (top..bot).filter { isPipeRow(doc, it) }
    }

    private fun isPipeRow(doc: com.intellij.openapi.editor.Document, line: Int): Boolean {
        val text = doc.charsSequence
            .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
            .toString().trim()
        return text.startsWith("|")
    }

    private fun isCommentOrBlank(doc: com.intellij.openapi.editor.Document, line: Int): Boolean {
        val text = doc.charsSequence
            .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
            .toString().trim()
        return text.isEmpty() || text.startsWith("#")
    }

    /** Parse `| a | b | c |` line into ["a", "b", "c"]. */
    fun parseRowCells(line: String): List<String> {
        val trimmed = line.trim()
        if (!trimmed.startsWith("|")) return emptyList()
        val parts = trimmed.split("|")
        if (parts.size <= 2) return emptyList()
        return parts.subList(1, parts.size - 1).map { it.trim() }
    }

    /** Rebuild a row line preserving original indent. Cell formatting is redone by format(). */
    fun buildRowLine(cells: List<String>, originalLine: String): String {
        val indent = originalLine.takeWhile { it == ' ' || it == '\t' }
        val sb = StringBuilder(indent)
        cells.forEach { sb.append("| ").append(it.trim()).append(' ') }
        sb.append('|')
        return sb.toString()
    }

    fun buildEmptyRow(indent: String, cellCount: Int): String {
        val sb = StringBuilder(indent)
        repeat(cellCount) { sb.append("|  ") }
        sb.append('|')
        return sb.toString()
    }
}
