/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.markdown

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl

/**
 * "Light" markdown rendering for Gherkin feature descriptions: folds the syntax
 * delimiters (`**`, `*`, `` ` ``, `[`, `](url)`) to a zero-width placeholder so when the
 * caret is away from the line, the description visually looks like rendered markdown:
 *
 *     **bold text**           →   bold text     (bold attributes from TzMarkdownAnnotator)
 *     *italic*                →   italic        (italic attributes)
 *     `code`                  →   code          (code attributes)
 *     [label](https://…)      →   label         (hyperlink attributes)
 *     ![alt](images/foo.svg)  →   alt           (— images themselves stay raw, "B" scope)
 *
 * IntelliJ auto-expands a fold region when the caret enters it, so editing the source
 * remains seamless. [TzMarkdownSyntaxAutoCollapse] re-collapses the folds on caret exit.
 *
 * Identification: all folds use [SYNTAX_PLACEHOLDER] (zero-width space, U+200B) so the
 * auto-collapse listener can match them without confusing them with other Cucumber+
 * folds.
 */
class TzMarkdownSyntaxFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val result = mutableListOf<FoldingDescriptor>()
        // Feature-level descriptions sit in a dedicated PSI wrapper.
        PsiTreeUtil.findChildrenOfType(root, GherkinFeatureHeaderImpl::class.java).forEach { hdr ->
            collectSyntaxFolds(hdr.text, hdr.textOffset, hdr.node, result)
        }
        // Scenario / Outline / Background / Rule descriptions — text slice between the
        // keyword and the first GherkinStep / GherkinExamplesBlock.
        PsiTreeUtil.findChildrenOfType(root, GherkinStepsHolder::class.java).forEach { holder ->
            if (holder is GherkinFeature) return@forEach
            val range = scenarioDescriptionTextRange(holder, document) ?: return@forEach
            val text = document.charsSequence.subSequence(range.startOffset, range.endOffset).toString()
            collectSyntaxFolds(text, range.startOffset, holder.node, result)
        }
        return result.toTypedArray()
    }

    private fun collectSyntaxFolds(
        text: String,
        base: Int,
        node: ASTNode,
        out: MutableList<FoldingDescriptor>
    ) {

        // Order matters: bold (**…**) must be detected BEFORE single-star italic (*…*),
        // and images (![alt](url)) BEFORE plain links ([text](url)). After each match we
        // record the consumed ranges so subsequent passes skip them.
        val consumed = sortedSetOf<Int>()

        fun consume(start: Int, end: Int) { for (i in start until end) consumed.add(i) }
        fun overlaps(start: Int, end: Int): Boolean {
            for (i in start until end) if (i in consumed) return true
            return false
        }

        fun foldRange(absStart: Int, absEnd: Int) {
            if (absEnd <= absStart) return
            out += FoldingDescriptor(node, TextRange(absStart, absEnd), null, SYNTAX_PLACEHOLDER, true, emptySet())
        }

        // 1) Images ![alt](url) — fold the `![` and `](url)` parts, keep `alt` visible.
        IMG_MD.findAll(text).forEach { m ->
            val openStart = m.range.first
            val openEnd = m.groups[1]!!.range.first   // before alt
            val closeStart = m.groups[1]!!.range.last + 1  // after alt
            val closeEnd = m.range.last + 1
            if (overlaps(openStart, closeEnd)) return@forEach
            foldRange(base + openStart, base + openEnd)
            foldRange(base + closeStart, base + closeEnd)
            consume(openStart, closeEnd)
        }

        // 2) Inline links [text](url) — same shape as images minus the leading `!`.
        LINK_MD.findAll(text).forEach { m ->
            val openStart = m.range.first
            val openEnd = m.groups[1]!!.range.first
            val closeStart = m.groups[1]!!.range.last + 1
            val closeEnd = m.range.last + 1
            if (overlaps(openStart, closeEnd)) return@forEach
            foldRange(base + openStart, base + openEnd)
            foldRange(base + closeStart, base + closeEnd)
            consume(openStart, closeEnd)
        }

        // 3) Bold **…** — fold each pair of `**`s (4 chars total hidden per bold span).
        BOLD_MD.findAll(text).forEach { m ->
            val openStart = m.range.first
            val openEnd = openStart + 2
            val closeStart = m.range.last - 1
            val closeEnd = m.range.last + 1
            if (overlaps(openStart, closeEnd)) return@forEach
            foldRange(base + openStart, base + openEnd)
            foldRange(base + closeStart, base + closeEnd)
            consume(openStart, closeEnd)
        }

        // 4) Italic *…* (single star). Skip start-of-line bullets `* item`.
        ITALIC_MD.findAll(text).forEach { m ->
            val openStart = m.range.first
            val openEnd = openStart + 1
            val closeStart = m.range.last
            val closeEnd = m.range.last + 1
            if (overlaps(openStart, closeEnd)) return@forEach
            // Don't fold a leading `* ` bullet (line-start star followed by space).
            if (openStart == 0 || text[openStart - 1] == '\n') {
                val after = if (openEnd < text.length) text[openEnd] else ' '
                if (after == ' ' || after == '\t') return@forEach
            }
            foldRange(base + openStart, base + openEnd)
            foldRange(base + closeStart, base + closeEnd)
            consume(openStart, closeEnd)
        }

        // 5) Inline code `…`
        CODE_MD.findAll(text).forEach { m ->
            val openStart = m.range.first
            val openEnd = openStart + 1
            val closeStart = m.range.last
            val closeEnd = m.range.last + 1
            if (overlaps(openStart, closeEnd)) return@forEach
            foldRange(base + openStart, base + openEnd)
            foldRange(base + closeStart, base + closeEnd)
            consume(openStart, closeEnd)
        }
    }

    override fun getPlaceholderText(node: ASTNode): String = SYNTAX_PLACEHOLDER
    override fun isCollapsedByDefault(node: ASTNode): Boolean = true

    companion object {
        /** Zero-width space — invisible to the reader, recognisable to our auto-collapse listener. */
        const val SYNTAX_PLACEHOLDER = "​"

        fun isMarkdownSyntaxPlaceholder(p: String?): Boolean = p == SYNTAX_PLACEHOLDER

        // Non-greedy capture for the inner content; anchored so we don't cross `]` boundaries.
        private val IMG_MD     = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")
        private val LINK_MD    = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
        // Greedy-less bold to avoid eating adjacent runs.
        private val BOLD_MD    = Regex("\\*\\*([^*]+)\\*\\*")
        private val ITALIC_MD  = Regex("\\*([^*\\n]+)\\*")
        private val CODE_MD    = Regex("`([^`\\n]+)`")
    }
}
