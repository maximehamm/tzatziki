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

import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableImpl

fun GherkinFile.findAllTables(): List<GherkinTable> {

    val tables = mutableListOf<GherkinTable>()
    class Visitor : GherkinElementVisitor(), PsiRecursiveVisitor {
        override fun visitElement(element: PsiElement) {
            ProgressIndicatorProvider.checkCanceled()
            element.acceptChildren(this)
        }
        override fun visitTable(table: GherkinTableImpl) {
            tables.add(table)
            super.visitTable(table)
        }
    }

    accept(Visitor())

    return tables
}
