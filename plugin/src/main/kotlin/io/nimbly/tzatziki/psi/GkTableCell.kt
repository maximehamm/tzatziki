package io.nimbly.tzatziki.psi

import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.awt.Point

val GherkinTableCell.row: GherkinTableRow
    get() = parent as GherkinTableRow

val GherkinTableCell.columnNumber: Int
    get() = row.psiCells.indexOf(this)

val GherkinTableCell.coordinate: Point
    get() {
        val row = row
        val y = row.table.allRows.indexOf(row)
        val x = row.psiCells.indexOf(this)
        return Point(x, y)
    }