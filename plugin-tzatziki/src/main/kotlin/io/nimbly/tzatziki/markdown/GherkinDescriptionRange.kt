/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.markdown

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

/**
 * Returns the absolute text range of the free-form description block inside a
 * [GherkinStepsHolder] (Scenario / Scenario Outline / Background / Rule).
 *
 * The description = the lines strictly between the holder's KEYWORD line and the line of
 * its first GherkinStep / GherkinExamplesBlock / GherkinTable child. Returns `null` when
 * the holder has no body yet, or no real description text between the keyword and body.
 *
 * GherkinFeature is excluded — its description text is already covered by the dedicated
 * `GherkinFeatureHeaderImpl` PSI element.
 */
fun scenarioDescriptionTextRange(holder: GherkinStepsHolder, document: Document): TextRange? {
    if (holder is GherkinFeature) return null

    val keywordLine = findHolderKeywordLine(holder, document) ?: return null

    val firstStep = holder.children.firstOrNull { it is GherkinStep } ?: return null
    val firstExamples = holder.children.firstOrNull { it is GherkinExamplesBlock }
    val firstStructural = listOfNotNull(firstStep, firstExamples)
        .minBy { it.textRange.startOffset }
    val structuralLine = document.getLineNumber(firstStructural.textRange.startOffset)

    if (structuralLine <= keywordLine + 1) return null
    val rawStart = document.getLineStartOffset(keywordLine + 1)
    val rawEnd = document.getLineEndOffset(structuralLine - 1)

    // Trim leading / trailing blank lines so the range hugs the actual prose.
    var firstLine = document.getLineNumber(rawStart)
    var lastLine = document.getLineNumber(maxOf(rawStart, rawEnd - 1))
    while (firstLine <= lastLine && isBlankLine(document, firstLine)) firstLine++
    while (lastLine >= firstLine && isBlankLine(document, lastLine)) lastLine--
    if (firstLine > lastLine) return null
    val start = document.getLineStartOffset(firstLine)
    val end = document.getLineEndOffset(lastLine)
    return if (end > start) TextRange(start, end) else null
}

private fun findHolderKeywordLine(holder: GherkinStepsHolder, document: Document): Int? {
    var node: ASTNode? = holder.node.firstChildNode
    while (node != null) {
        val t = node.elementType
        if (t == GherkinTokenTypes.SCENARIO_KEYWORD ||
            t == GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD ||
            t == GherkinTokenTypes.BACKGROUND_KEYWORD ||
            t == GherkinTokenTypes.RULE_KEYWORD ||
            t == GherkinTokenTypes.EXAMPLE_KEYWORD ||
            t == GherkinTokenTypes.FEATURE_KEYWORD) {
            return document.getLineNumber(node.startOffset)
        }
        node = node.treeNext
    }
    return null
}

private fun isBlankLine(doc: Document, line: Int): Boolean =
    doc.charsSequence.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line)).isBlank()
