package io.nimbly.tzatziki.util

import io.nimbly.tzatziki.psi.allRows
import io.nimbly.tzatziki.psi.table
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

val GherkinTableCell.row: GherkinTableRow
    get() = parent as GherkinTableRow

val GherkinTableCell.coordinate: Pair<Int, Int>
    get() {
        val row = row
        val y = row.table.allRows.indexOf(row)
        val x = row.psiCells.indexOf(this)
        return Pair(x, y)
    }