/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinPystring
import org.jetbrains.plugins.cucumber.psi.GherkinTable

class TzFoldingContributor : FoldingBuilderEx() {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        buildMultiLineStrings(descriptors, root.children)
        return descriptors.toTypedArray<FoldingDescriptor>()
    }

    private fun buildMultiLineStrings(descriptors: MutableList<FoldingDescriptor>, children: Array<PsiElement>) {

        children.forEach { c ->

            if (c is GherkinPystring) {

                val textOffset = c.firstChild.textOffset
                val endOffset = c.lastChild.textRange.endOffset
                descriptors.add(
                    object : FoldingDescriptor(
                        c.firstChild.node, TextRange(textOffset, endOffset),
                        FoldingGroup.newGroup("MultiLineLiteral${c.hashCode()}")
                    ) {
                        override fun getPlaceholderText(): String {

                            val t = c.text.substring(3).trim().substringBefore("\n");
                            return "\"\"\"$t...\"\"\""
                        }
                    })
            }
            else if (c is GherkinTable) {

                val textOffset = c.firstChild.textOffset
                val endOffset = c.lastChild.textRange.endOffset
                descriptors.add(
                    object : FoldingDescriptor(
                        c.firstChild.node, TextRange(textOffset, endOffset),
                        FoldingGroup.newGroup("Table${c.hashCode()}")) {
                        override fun getPlaceholderText(): String {
                            val t = c.headerRow?.text ?: c.dataRows.first().text
                            return "$t ..."
                        }
                    })
            }
            else {
                buildMultiLineStrings(descriptors, c.children)
            }
        }
        }

        override fun getPlaceholderText(node: ASTNode): String {
            return "..."
        }

        override fun isCollapsedByDefault(node: ASTNode): Boolean {
            return false
        }
    }
