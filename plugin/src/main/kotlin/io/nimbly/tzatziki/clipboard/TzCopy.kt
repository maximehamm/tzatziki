/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

package io.nimbly.tzatziki.clipboard

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import io.nimbly.tzatziki.psi.cellsInRange
import io.nimbly.tzatziki.util.findTableAt
import io.nimbly.tzatziki.util.stopBeforeDeletion
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.StringReader

fun Editor.smartCut(): Boolean {

    if (!smartCopy())
        return false

    if (stopBeforeDeletion(true, true))
        return true

    return false
}

fun Editor.smartCopy(): Boolean {

    if (! isColumnMode) return false
    val offset = selectionModel.selectionStart
    val table = findTableAt(offset) ?: return false
    if (!selectionModel.getSelectedText(true)!!.contains("|")) return false

    // Prepare table content
    val transferable = TzatzikiTransferableData(table, selectionModel.blockSelectionStarts, selectionModel.blockSelectionEnds)

    // Add to clipboard
    CopyPasteManager.getInstance().setContents(transferable)
    return true
}

private fun GherkinTable.getTransferable(dataFlavor: DataFlavor, starts: IntArray, ends: IntArray): String {

    val raw = dataFlavor == RawText.getDataFlavor()
    val sb = StringBuilder()

    for (i in starts.indices) {

        val r = TextRange(starts[i], ends[i])
        val cells: List<GherkinTableCell> = cellsInRange(r)

        if (cells.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append('\n')

        for (j in cells.indices) {

            val c = cells[j]
            var inter = c.textRange.intersection(r)

            if (inter != null) {
                inter = inter.shiftRight(-c.textOffset)
                sb.append(if (raw) inter.substring(c.text) else c.text.trim())
            }

            if (j < cells.size-1)
                sb.append('\t')
        }
    }
    return sb.toString()
}

@Suppress("JoinDeclarationAndAssignment")
private class TzatzikiTransferableData(table: GherkinTable, startOffsets: IntArray, endOffsets: IntArray) : Transferable {

    private val myTransferDataFlavors: List<DataFlavor>
    private val text: String
    private val textAsRaw: RawText

    override fun getTransferDataFlavors()
        = myTransferDataFlavors.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor)
        = transferDataFlavors.find { it == flavor } != null

    override fun getTransferData(flavor: DataFlavor): Any {
        try {
            when {
                Comparing.equal(RawText.getDataFlavor(), flavor) -> return textAsRaw
                DataFlavor.stringFlavor.equals(flavor) -> return text
                DataFlavor.plainTextFlavor.equals(flavor) -> return StringReader(text)
            }
        } catch (e: NoClassDefFoundError) {
        }
        throw UnsupportedFlavorException(flavor)
    }

    init {
        myTransferDataFlavors = listOfNotNull(
            DataFlavor.stringFlavor, DataFlavor.plainTextFlavor, RawText.getDataFlavor()
        )
        textAsRaw = RawText(table.getTransferable(RawText.getDataFlavor(), startOffsets, endOffsets))
        text = table.getTransferable(DataFlavor.stringFlavor, startOffsets, endOffsets)
    }
}

