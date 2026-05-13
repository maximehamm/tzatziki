/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Folds `# @header: row` / `# @header: column` comments into a discreet "Header row" /
 * "Header column" placeholder. The fold collapses by default; the [CaretListener] in
 * [TzHeaderCommentAutoCollapse] re-collapses on caret exit, while the platform handles
 * the auto-expand on caret entry.
 *
 * Scans the document line by line rather than walking PSI: the cucumber-plugin Gherkin
 * lexer represents comments differently across language directives (`# language: zh-CN`
 * etc.), and PSI traversal can miss our annotation in localised feature files. Plain
 * text scanning is dialect-agnostic.
 */
class TzHeaderCommentFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val result = mutableListOf<FoldingDescriptor>()
        val chars = document.charsSequence
        for (line in 0 until document.lineCount) {
            val s = document.getLineStartOffset(line)
            val e = document.getLineEndOffset(line)
            val text = chars.subSequence(s, e).toString()
            val placeholder = when {
                HEADER_ROW.matches(text)    -> "Header row"
                HEADER_COLUMN.matches(text) -> "Header column"
                else -> continue
            }
            val firstNonWs = text.indexOfFirst { !it.isWhitespace() }
            if (firstNonWs < 0) continue
            val start = s + firstNonWs
            val node = root.findElementAt(start)?.node ?: root.node
            result += FoldingDescriptor(node, TextRange(start, e), null, placeholder)
        }
        return result.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String {
        val t = node.text
        return when {
            HEADER_ROW.matches(t)    -> "Header row"
            HEADER_COLUMN.matches(t) -> "Header column"
            else -> "Header"
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true

    companion object {
        internal val HEADER_ROW    = Regex("^\\s*#\\s*@header:\\s*row\\s*$")
        internal val HEADER_COLUMN = Regex("^\\s*#\\s*@header:\\s*column\\s*$")
    }
}
