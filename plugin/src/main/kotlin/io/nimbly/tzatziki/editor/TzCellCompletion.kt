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

package io.nimbly.tzatziki.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import icons.ActionIcons
import io.nimbly.tzatziki.psi.*
import io.nimbly.tzatziki.util.findTableAt
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl

class TzCellCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val cell = parameters.position.parent
        if (cell !is GherkinTableCell)
            return

        if (cell.parent is GherkinTableHeaderRowImpl) {
            completeHeader(cell, resultSet)
        }
        else {
            completeData(cell, resultSet)
        }
    }

    private fun completeHeader(cell: GherkinTableCell, resultSet: CompletionResultSet) {

        // Find all tables
        val file = cell.containingFile
        if (file !is GherkinFile)
            return
        val tables = file.findAllTables()

        // Find all values
        val values: Set<String> = tables
            .mapNotNull { it.headerRow }
            .flatMap { it.psiCells }
            .filter { it != cell }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        // Add all values to completion
        values.forEach { value ->
            val lookup = LookupElementBuilder.create(value)
                .withPresentableText(value)
                .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                .withInsertHandler { context, _ ->
                    context.editor.findTableAt(context.startOffset)?.format(false)
                }
            resultSet.addElement(lookup)
        }
    }

    private fun completeData(cell: GherkinTableCell, resultSet: CompletionResultSet) {

        // Find column name
        val columnName = cell.row.table.headerRow?.psiCells?.get(cell.columnNumber)?.text?.trim()
            ?: return

        // Find all tables
        val file = cell.containingFile
        if (file !is GherkinFile)
            return
        val tables = file.findAllTables()

        // Find all values
        val values = mutableListOf<String>()
        tables.forEach { table ->
            val header = table.headerRow
            if (header != null) {
                val index = header.psiCells.indexOfFirst { it.text.trim() == columnName }
                if (index >= 0) {
                    table.dataRows.forEach { row ->
                        val c = row.cell(index)
                        if (c!=cell) {
                            val txt = c.text.trim()
                            if (txt.isNotBlank())
                                values.add(txt)
                        }
                    }
                }
            }
        }

        // Group by number of use
        val groupBy: Map<String, Int>
                = values.groupBy { it }.map { it.key to it.value.size }.toMap()

        // Add all values to completion
        groupBy.forEach { (value, count) ->
            val lookup = LookupElementBuilder.create(value)
                .withPresentableText(value)
                .withTypeText("(used $count times)")
                .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                .withInsertHandler { context, _ ->
                    context.editor.findTableAt(context.startOffset)?.format(false)
                }
            resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0*count))
        }
    }

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet)
                    = complete(parameters, context, resultSet)
            }
        )
    }
}