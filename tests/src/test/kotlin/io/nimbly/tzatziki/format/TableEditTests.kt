/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase
import io.nimbly.tzatziki.util.TableEditOps

/**
 * Verifies row/column insert & delete on Gherkin tables (the actions exposed in the
 * table-frame popup), including the comment-preservation contract: lines that don't
 * start with `|` (comments, blank lines) inside the table region must remain
 * untouched.
 */
class TableEditTests : AbstractTestCase() {

    // ------------------------------------------------------------------
    // Row operations
    // ------------------------------------------------------------------

    fun testInsertRowAbove() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Rows
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  | 3 | 4 |
            """)
        setCursor("| 1 | 2 |")

        addRowAbove()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Rows
                Given the table:
                  | A | B |
                  |   |   |
                  | 1 | 2 |
                  | 3 | 4 |
            """)
    }

    fun testInsertRowBelow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Rows
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  | 3 | 4 |
            """)
        setCursor("| 1 | 2 |")

        addRowBelow()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Rows
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  |   |   |
                  | 3 | 4 |
            """)
    }

    fun testDeleteRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Rows
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  | 3 | 4 |
            """)
        setCursor("| 1 | 2 |")

        deleteRow()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Rows
                Given the table:
                  | A | B |
                  | 3 | 4 |
            """)
    }

    // ------------------------------------------------------------------
    // Column operations
    // ------------------------------------------------------------------

    fun testInsertColumnLeft() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Cols
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 2")

        addColumnLeft()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Cols
                Given the table:
                  | A |  | B | C |
                  | 1 |  | 2 | 3 |
            """)
    }

    fun testInsertColumnRight() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Cols
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 2")

        addColumnRight()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Cols
                Given the table:
                  | A | B |  | C |
                  | 1 | 2 |  | 3 |
            """)
    }

    fun testDeleteColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Cols
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 2")

        deleteColumn()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Cols
                Given the table:
                  | A | C |
                  | 1 | 3 |
            """)
    }

    // ------------------------------------------------------------------
    // Edge cases — first / last row
    // ------------------------------------------------------------------

    fun testInsertRowAboveFirstRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: First row
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
        setCursor("| A | B |")

        addRowAbove()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: First row
                Given the table:
                  |   |   |
                  | A | B |
                  | 1 | 2 |
            """)
    }

    fun testInsertRowBelowFirstRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: First row
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
        setCursor("| A | B |")

        addRowBelow()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: First row
                Given the table:
                  | A | B |
                  |   |   |
                  | 1 | 2 |
            """)
    }

    fun testInsertRowAboveLastRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Last row
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
        setCursor("| 1 | 2 |")

        addRowAbove()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Last row
                Given the table:
                  | A | B |
                  |   |   |
                  | 1 | 2 |
            """)
    }

    fun testInsertRowBelowLastRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Last row
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
        setCursor("| 1 | 2 |")

        addRowBelow()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Last row
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  |   |   |
            """)
    }

    fun testDeleteFirstRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Delete first row
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  | 3 | 4 |
            """)
        setCursor("| A | B |")

        deleteRow()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Delete first row
                Given the table:
                  | 1 | 2 |
                  | 3 | 4 |
            """)
    }

    fun testDeleteLastRow() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Delete last row
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  | 3 | 4 |
            """)
        setCursor("| 3 | 4 |")

        deleteRow()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Delete last row
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
    }

    // ------------------------------------------------------------------
    // Edge cases — first / last column
    // ------------------------------------------------------------------

    fun testInsertColumnLeftFirstColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: First col
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 1")

        addColumnLeft()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: First col
                Given the table:
                  |  | A | B | C |
                  |  | 1 | 2 | 3 |
            """)
    }

    fun testInsertColumnRightFirstColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: First col
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 1")

        addColumnRight()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: First col
                Given the table:
                  | A |  | B | C |
                  | 1 |  | 2 | 3 |
            """)
    }

    fun testInsertColumnLeftLastColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Last col
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 3")

        addColumnLeft()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Last col
                Given the table:
                  | A | B |  | C |
                  | 1 | 2 |  | 3 |
            """)
    }

    fun testInsertColumnRightLastColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Last col
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 3")

        addColumnRight()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Last col
                Given the table:
                  | A | B | C |  |
                  | 1 | 2 | 3 |  |
            """)
    }

    fun testDeleteFirstColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Delete first col
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 1")

        deleteColumn()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Delete first col
                Given the table:
                  | B | C |
                  | 2 | 3 |
            """)
    }

    fun testDeleteLastColumn() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Delete last col
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 3")

        deleteColumn()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Delete last col
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
    }

    // ------------------------------------------------------------------
    // Edge cases — shift at boundaries (must be no-op, never corrupt)
    // ------------------------------------------------------------------

    fun testShiftUpFirstRowIsNoOp() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Shift first up
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
        setCursor(" A")

        moveUp()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Shift first up
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
    }

    fun testShiftDownLastRowIsNoOp() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Shift last down
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
        setCursor(" 1")

        moveDown()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Shift last down
                Given the table:
                  | A | B |
                  | 1 | 2 |
            """)
    }

    fun testShiftLeftFirstColumnIsNoOp() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Shift first left
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 1")

        moveLeft()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Shift first left
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
    }

    fun testShiftRightLastColumnIsNoOp() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Shift last right
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
        setCursor(" 3")

        moveRight()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Shift last right
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
            """)
    }

    // ------------------------------------------------------------------
    // Comment preservation (the regression case)
    // ------------------------------------------------------------------

    fun testInsertRowPreservesInterleavedComment() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: With comment
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # mid-table comment
                  | 3 | 4 |
            """)
        setCursor("| 3 | 4 |")

        addRowAbove()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: With comment
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # mid-table comment
                  |   |   |
                  | 3 | 4 |
            """)
    }

    fun testShiftUpPreservesInterleavedComment() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Shift with comment
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # mid-table comment
                  | 3 | 4 |
            """)
        setCursor(" 4")

        moveUp()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Shift with comment
                Given the table:
                  | A | B |
                  | 3 | 4 |
                  # mid-table comment
                  | 1 | 2 |
            """)
    }

    fun testShiftLeftPreservesInterleavedComment() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Shift col with comment
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
                  # mid-table comment
                  | 4 | 5 | 6 |
            """)
        setCursor(" 5")

        moveLeft()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Shift col with comment
                Given the table:
                  | B | A | C |
                  | 2 | 1 | 3 |
                  # mid-table comment
                  | 5 | 4 | 6 |
            """)
    }

    fun testInsertRowPreservesBlankLineAndMultipleComments() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Mixed
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # first comment

                  # second comment
                  | 3 | 4 |
            """)
        setCursor("| 3 | 4 |")

        addRowAbove()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Mixed
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # first comment

                  # second comment
                  |   |   |
                  | 3 | 4 |
            """)
    }

    fun testDeleteColumnPreservesBlankLineAndMultipleComments() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Mixed
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
                  # first comment

                  # second comment
                  | 4 | 5 | 6 |
            """)
        setCursor(" 5")

        deleteColumn()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Mixed
                Given the table:
                  | A | C |
                  | 1 | 3 |
                  # first comment

                  # second comment
                  | 4 | 6 |
            """)
    }

    fun testShiftDownPreservesMultipleCommentsBetween() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Mixed shift
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # first
                  # second

                  | 3 | 4 |
            """)
        setCursor(" 1")

        moveDown()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Mixed shift
                Given the table:
                  | A | B |
                  | 3 | 4 |
                  # first
                  # second

                  | 1 | 2 |
            """)
    }

    fun testDeleteColumnFromFragmentMustAffectFullLogicalTable() {

        // Bug case: when the popup is opened on a fragment of a table split by a
        // comment, the action's tableLines are limited to that fragment's PSI table.
        // We simulate that by passing only the upper fragment's row lines.
        // The expected (correct) behavior is that the action applies to the WHOLE
        // logical table, not just the fragment.

        // language=feature
        feature("""
            Feature: Test
              Scenario: Popup-fragment scope
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
                  # comment
                  | 4 | 5 | 6 |
            """)
        setCursor(" 2")

        // Simulate popup behaviour: tableLines come from the upper fragment only
        // (geometry is limited to one PSI table). The fix must compensate.
        applyOpWithFragmentScope(TableEditOps.Op.DeleteColumn(atIndex = 1))

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Popup-fragment scope
                Given the table:
                  | A | C |
                  | 1 | 3 |
                  # comment
                  | 4 | 6 |
            """)
    }

    /**
     * Simulates how TzTableDecorator's popup currently passes tableLines: only the
     * lines belonging to the PSI table that contains the caret offset, not the full
     * logical table that may span comments.
     */
    private fun applyOpWithFragmentScope(op: TableEditOps.Op) {
        val editor = myFixture.editor
        val doc = editor.document
        val offset = editor.caretModel.offset
        // Mimic TzTableDecorator's geometry: walk up/down from the caret line as long as
        // the next line is also a `|` row (no skipping across comments / blanks).
        val cursorLine = doc.getLineNumber(offset)
        var top = cursorLine
        while (top > 0 && doc.text.substring(doc.getLineStartOffset(top - 1), doc.getLineEndOffset(top - 1))
                .trim().startsWith("|")) top--
        var bot = cursorLine
        while (bot < doc.lineCount - 1 && doc.text.substring(doc.getLineStartOffset(bot + 1), doc.getLineEndOffset(bot + 1))
                .trim().startsWith("|")) bot++
        val fragmentTableLines = (top..bot).toList()
        TableEditOps.apply(editor, fragmentTableLines, op)
    }

    fun testDeleteColumnAffectsAllRowsAcrossComments() {

        // Bug case: delete column from a row located on the upper fragment of a table
        // that's split by an intercalated comment. The column must be removed from
        // ALL rows of the logical table — both above AND below the comment.

        // language=feature
        feature("""
            Feature: Test
              Scenario: Cross-fragment delete
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
                  # mid-table comment
                  | 4 | 5 | 6 |
                  | 7 | 8 | 9 |
            """)
        // Cursor in the UPPER fragment, on column B (the column to delete).
        setCursor(" 2")

        deleteColumn()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Cross-fragment delete
                Given the table:
                  | A | C |
                  | 1 | 3 |
                  # mid-table comment
                  | 4 | 6 |
                  | 7 | 9 |
            """)
    }

    fun testInsertColumnAffectsAllRowsAcrossComments() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: Cross-fragment insert
                Given the table:
                  | A | B |
                  | 1 | 2 |
                  # comment
                  | 3 | 4 |
            """)
        setCursor(" 1")

        addColumnRight()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: Cross-fragment insert
                Given the table:
                  | A |  | B |
                  | 1 |  | 2 |
                  # comment
                  | 3 |  | 4 |
            """)
    }

    fun testDeleteColumnPreservesInterleavedComment() {

        // language=feature
        feature("""
            Feature: Test
              Scenario: With comment
                Given the table:
                  | A | B | C |
                  | 1 | 2 | 3 |
                  # mid-table comment
                  | 4 | 5 | 6 |
            """)
        setCursor(" 2")

        deleteColumn()

        // language=feature
        checkContent("""
            Feature: Test
              Scenario: With comment
                Given the table:
                  | A | C |
                  | 1 | 3 |
                  # mid-table comment
                  | 4 | 6 |
            """)
    }

    // ------------------------------------------------------------------
    // Action helpers — calque sur moveRight()/moveDown() de AbstractTestCase
    // ------------------------------------------------------------------

    private fun addRowAbove()    = applyOp(TableEditOps.Op.InsertRow(rowIdxAtCursor(), above = true))
    private fun addRowBelow()    = applyOp(TableEditOps.Op.InsertRow(rowIdxAtCursor(), above = false))
    private fun deleteRow()      = applyOp(TableEditOps.Op.DeleteRow(rowIdxAtCursor()))
    private fun addColumnLeft()  = applyOp(TableEditOps.Op.InsertColumn(colIdxAtCursor(), before = true))
    private fun addColumnRight() = applyOp(TableEditOps.Op.InsertColumn(colIdxAtCursor(), before = false))
    private fun deleteColumn()   = applyOp(TableEditOps.Op.DeleteColumn(colIdxAtCursor()))

    private fun applyOp(op: TableEditOps.Op) {
        val editor = myFixture.editor
        val cursorLine = editor.document.getLineNumber(editor.caretModel.offset)
        val tableLines = TableEditOps.collectTableLinesAround(editor, cursorLine)
        TableEditOps.apply(editor, tableLines, op)
    }

    /** Index (in the filtered list of `|`-rows) of the row containing the caret. */
    private fun rowIdxAtCursor(): Int {
        val editor = myFixture.editor
        val cursorLine = editor.document.getLineNumber(editor.caretModel.offset)
        val tableLines = TableEditOps.collectTableLinesAround(editor, cursorLine)
        return tableLines.indexOf(cursorLine).coerceAtLeast(0)
    }

    /** Column index (0-based) of the cell containing the caret. */
    private fun colIdxAtCursor(): Int {
        val editor = myFixture.editor
        val doc = editor.document
        val offset = editor.caretModel.offset
        val line = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(line)
        val before = doc.charsSequence.subSequence(lineStart, offset).toString()
        // Number of '|' before the cursor on this line, minus 1 (the leading pipe).
        val pipes = before.count { it == '|' }
        return (pipes - 1).coerceAtLeast(0)
    }
}
