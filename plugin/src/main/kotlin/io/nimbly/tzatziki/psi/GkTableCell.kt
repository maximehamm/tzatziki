/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.psi

import com.intellij.openapi.util.TextRange
import io.nimbly.tzatziki.util.nextPipe
import io.nimbly.tzatziki.util.previousPipe
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

val GherkinTableCell.fullRange: TextRange
    get() = TextRange(previousPipe.textOffset+1, nextPipe.textOffset)