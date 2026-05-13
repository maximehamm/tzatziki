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
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl

/**
 * Folds the free-form description text inside any `GherkinStepsHolder` (Feature, Business
 * Need, Background, Rule, Scenario, Scenario Outline).
 *
 * Detection is PSI-based — dialect-aware, so works on `Feature:`/`场景:`/`Fonctionnalité:`
 * alike. Two tricky bits the cucumber-plugin parses in non-obvious ways:
 *
 *  - `GherkinFeatureHeaderImpl` agglomerates *all* feature-level prologues (Business Need
 *    + Feature etc.). Its content is exclusively leaf AST tokens (TEXT, FEATURE_KEYWORD,
 *    COLON) — `PsiElement.children` returns empty. We walk `node.getChildren(null)` and
 *    split on every internal FEATURE_KEYWORD to recover individual description blocks.
 *
 *  - `GherkinScenarioImpl.textRange` starts at the *tag* line, not the keyword line. We
 *    locate the actual SCENARIO_KEYWORD (or _OUTLINE/_BACKGROUND/_RULE/_EXAMPLE) leaf to
 *    know where the description must start.
 */
class TzDescriptionFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val result = mutableListOf<FoldingDescriptor>()

        PsiTreeUtil.findChildrenOfType(root, GherkinFeatureHeaderImpl::class.java).forEach { hdr ->
            splitFeatureHeaderRanges(hdr, document).forEach { range ->
                addFold(range, document, hdr.node, result)
            }
        }

        PsiTreeUtil.findChildrenOfType(root, GherkinStepsHolder::class.java).forEach { holder ->
            if (holder is GherkinFeature) return@forEach
            val range = scenarioDescriptionRange(holder, document) ?: return@forEach
            addFold(range, document, holder.node, result)
        }

        return result.toTypedArray()
    }

    /**
     * Split GherkinFeatureHeader's leaf AST stream on each FEATURE_KEYWORD into one
     * description block per logical prologue (Business Need / Feature / Ability).
     */
    private fun splitFeatureHeaderRanges(hdr: GherkinFeatureHeaderImpl, document: Document): List<TextRange> {
        val blocks = mutableListOf<TextRange>()
        var blockStart = -1
        var blockEnd = -1
        // The header's own outer FEATURE_KEYWORD sits at the parent level (before the header
        // begins). Seed lastKeywordLine accordingly: anything on the line just above the
        // header's first offset is considered the keyword line of the first block.
        var lastKeywordLine = document.getLineNumber(hdr.textRange.startOffset) - 1

        fun flush() {
            if (blockStart in 0 until blockEnd) blocks += TextRange(blockStart, blockEnd)
            blockStart = -1; blockEnd = -1
        }

        var node: ASTNode? = hdr.node.firstChildNode
        while (node != null) {
            val type = node.elementType
            when (type) {
                GherkinTokenTypes.FEATURE_KEYWORD -> {
                    flush()
                    lastKeywordLine = document.getLineNumber(node.startOffset)
                }
                GherkinTokenTypes.TEXT -> {
                    // Only TEXT leaves count as description content. Anything else
                    // (TAG / SCENARIO_KEYWORD / EXAMPLES_KEYWORD / …) that the parser
                    // may have accidentally lumped inside the header is ignored, so we
                    // never extend the fold over a step or scenario header.
                    if (!node.text.isBlank()) {
                        val nodeStartLine = document.getLineNumber(node.startOffset)
                        if (nodeStartLine > lastKeywordLine) {
                            if (blockStart < 0) blockStart = node.startOffset
                            blockEnd = node.startOffset + node.textLength
                        }
                    }
                }
                else -> { /* ignore COLON and any other token type */ }
            }
            node = node.treeNext
        }
        flush()

        return blocks.mapNotNull { tightenToLineSpan(it, document) }
    }

    private fun scenarioDescriptionRange(holder: GherkinStepsHolder, document: Document): TextRange? {
        // Locate the keyword leaf token (SCENARIO_KEYWORD / SCENARIO_OUTLINE_KEYWORD /
        // BACKGROUND_KEYWORD / RULE_KEYWORD / EXAMPLE_KEYWORD) inside the holder. The
        // holder's textRange can start at a TAG line, so we cannot use it as the keyword
        // anchor.
        val keywordLine = findKeywordLine(holder, document) ?: return null

        // Safety: only fold a scenario description when the parser has actually recognised
        // at least one real GherkinStep inside this holder. In dialects the cucumber-plugin
        // doesn't parse (Chinese, …), step keywords stay as flat TEXT leaves and would
        // otherwise be mistaken for description content — folding over real step lines.
        val firstStep = holder.children.firstOrNull { it is GherkinStep }
            ?: return null
        // Body start = the first real step (we already know one exists), or an earlier
        // examples block if any.
        val firstExamples = holder.children.firstOrNull { it is GherkinExamplesBlock }
        val firstStructural = listOfNotNull(firstStep, firstExamples)
            .minBy { it.textRange.startOffset }
        val structuralStartLine = document.getLineNumber(firstStructural.textRange.startOffset)

        if (structuralStartLine <= keywordLine + 1) return null
        val rawStart = document.getLineStartOffset(keywordLine + 1)
        val rawEnd = document.getLineEndOffset(structuralStartLine - 1)
        return tightenToLineSpan(TextRange(rawStart, rawEnd), document)
    }

    private fun findKeywordLine(holder: GherkinStepsHolder, document: Document): Int? {
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

    private fun tightenToLineSpan(range: TextRange, document: Document): TextRange? {
        var firstLine = document.getLineNumber(range.startOffset)
        var lastLine = document.getLineNumber(maxOf(range.startOffset, range.endOffset - 1))
        while (firstLine <= lastLine && isBlankLine(document, firstLine)) firstLine++
        while (lastLine >= firstLine && isBlankLine(document, lastLine)) lastLine--
        if (firstLine > lastLine) return null
        val lineStart = document.getLineStartOffset(firstLine)
        val firstNonWs = document.charsSequence
            .subSequence(lineStart, document.getLineEndOffset(firstLine))
            .indexOfFirst { !it.isWhitespace() }
            .coerceAtLeast(0)
        val start = lineStart + firstNonWs
        val end = document.getLineEndOffset(lastLine)
        return if (end > start) TextRange(start, end) else null
    }

    private fun addFold(
        range: TextRange,
        document: Document,
        node: ASTNode,
        result: MutableList<FoldingDescriptor>
    ) {
        if (range.length <= 0) return
        val startLine = document.getLineNumber(range.startOffset)
        val firstLineText = document.charsSequence
            .subSequence(document.getLineStartOffset(startLine), document.getLineEndOffset(startLine))
            .toString().trim()
        val placeholder = "📝 " + stripMarkdown(firstLineText).take(80)
        result += FoldingDescriptor(node, range, null, placeholder)
    }

    override fun getPlaceholderText(node: ASTNode): String = "📝 Description"

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}

private fun isBlankLine(doc: Document, line: Int): Boolean =
    doc.charsSequence.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line)).isBlank()

private fun stripMarkdown(s: String): String = s
    .replace(Regex("!\\[(.*?)]\\(.*?\\)"), "$1")
    .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
    .replace(Regex("__(.*?)__"), "$1")
    .replace(Regex("\\*(.*?)\\*"), "$1")
    .replace(Regex("_(.*?)_"), "$1")
    .replace(Regex("`(.*?)`"), "$1")
