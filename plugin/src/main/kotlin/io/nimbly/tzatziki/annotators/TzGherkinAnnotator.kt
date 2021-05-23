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

package io.nimbly.tzatziki.annotators

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.impl.GherkinScenarioOutlineImpl
import org.jetbrains.plugins.cucumber.psi.refactoring.rename.CucumberStepRenameProcessor
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import java.util.regex.Pattern

//https://youtrack.jetbrains.com/issue/IDEA-269898
@Deprecated("Remove this when Jetbrain issue IDEA-269898 will be fixed")
class TzGherkinAnnotator : Annotator {
    override fun annotate(psiElement: PsiElement, holder: AnnotationHolder) {
        psiElement.accept(TzGherkinAnnotatorVisitor(holder))
    }
}

//https://youtrack.jetbrains.com/issue/IDEA-269898
@Deprecated("Remove this when Jetbrain issue IDEA-269898 will be fixed")
class TzGherkinAnnotatorVisitor(private val myHolder: AnnotationHolder) : GherkinElementVisitor() {

    private fun highlight(element: PsiElement, colorKey: TextAttributesKey) {
        myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(colorKey).create()
    }

    private fun highlight(element: PsiElement, range: TextRange, colorKey: TextAttributesKey) {
        val range1 = range.shiftRight(element.textOffset)
        myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range1).textAttributes(colorKey).create()
    }

    override fun visitElement(element: PsiElement) {
        ProgressManager.checkCanceled()
        super.visitElement(element)
        val textInsideScenario =
            PsiUtilCore.getElementType(element) === GherkinTokenTypes.TEXT && element.parent is GherkinStepsHolder
        if (textInsideScenario && hasStepsBefore(element)) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, CucumberBundle.message("gherkin.lexer.unexpected.element"))
                .create()
        }
    }

    override fun visitStep(step: GherkinStep) {
        val reference = CucumberStepRenameProcessor.getCucumberStepReference(step) ?: return
        val definition = reference.resolveToDefinition()
        if (definition != null) {
            val parameterRanges = GherkinPsiUtil.buildParameterRanges(
                step, definition,
                reference.rangeInElement.startOffset
            ) ?: return
            for (range in parameterRanges) {
                if (range.length > 0) {
                    highlight(step, range, GherkinHighlighter.REGEXP_PARAMETER)
                }
            }
            highlightOutlineParams(step, reference)
        }
    }

    override fun visitScenarioOutline(outline: GherkinScenarioOutline) {
        super.visitScenarioOutline(outline)
        val params = PsiTreeUtil.getChildrenOfType(outline, GherkinStepParameter::class.java)
        if (params != null) {
            for (param in params) {
                highlight(param, GherkinHighlighter.OUTLINE_PARAMETER_SUBSTITUTION)
            }
        }
        val braces = outline.node.getChildren(TokenSet.create(GherkinTokenTypes.STEP_PARAMETER_BRACE))
        for (brace in braces) {
            highlight(brace.psi, GherkinHighlighter.REGEXP_PARAMETER)
        }
    }

    private fun highlightOutlineParams(step: GherkinStep, reference: CucumberStepReference) {
        val realSubstitutions = getRealSubstitutions(step)
        if (realSubstitutions != null && realSubstitutions.isNotEmpty()) {
            // regexp for searching outline parameters substitutions
            val regexp = StringBuilder()
            regexp.append("<(")
            for (substitution in realSubstitutions) {
                if (regexp.length > 2) {
                    regexp.append("|")
                }
                regexp.append(Pattern.quote(substitution))
            }
            regexp.append(")>")

            // for each substitution - add highlighting
            val pattern = Pattern.compile(regexp.toString())

            // highlight in step name
            val textStartOffset = reference.rangeInElement.startOffset
            highlightOutlineParamsForText(step.name, textStartOffset, pattern, step)

            // highlight in pystring
            val pystring = step.pystring
            if (pystring != null) {
                val textOffset = pystring.textOffset - step.textOffset
                highlightOutlineParamsForText(pystring.text, textOffset, pattern, step)
            }

            // highlight in table
            val table: PsiElement? = step.table
            if (table != null) {
                val textOffset = table.textOffset - step.textOffset
                highlightOutlineParamsForText(table.text, textOffset, pattern, step)
            }
        }
    }

    private fun highlightOutlineParamsForText(
        text: String, textStartInElementOffset: Int, pattern: Pattern,
        step: GherkinStep
    ) {
        if (StringUtil.isEmpty(text))
            return

        val matcher = pattern.matcher(text)
        var result = matcher.find()
        if (!result)
            return

        do {
            val substitution = matcher.group(1)
            if (!StringUtil.isEmpty(substitution)) {
                val start = matcher.start(1)
                val end = matcher.end(1)
                val range = TextRange(start, end).shiftRight(textStartInElementOffset)
                highlight(step, range, GherkinHighlighter.OUTLINE_PARAMETER_SUBSTITUTION)
            }
            result = matcher.find()
        } while (result)
    }

    companion object {
        private fun hasStepsBefore(element: PsiElement): Boolean {
            var el: PsiElement? = element.prevSibling
            while (el != null && el !is GherkinStep) {
                el = el.prevSibling
            }
            return el != null
        }

        private fun getRealSubstitutions(step: GherkinStep): List<String>? {
            val possibleSubstitutions = step.paramsSubstitutions
            if (possibleSubstitutions.isEmpty())
                return null

            // get step definition
            val holder = step.stepHolder

            // if step is in Scenario Outline
            if (holder !is GherkinScenarioOutlineImpl)
                return null

            // then get header cell
            val examplesBlocks = holder.examplesBlocks
            if (examplesBlocks.isEmpty())
                return null

            val table = examplesBlocks[0].table ?: return null
            val header = table.headerRow!!
            val headerCells = header.psiCells

            // fetch headers
            val headers: MutableList<String> = ArrayList(headerCells.size + 1)
            for (headerCell in headerCells) {
                headers.add(headerCell.text.trim { it <= ' ' })
            }
            // filter used substitutions names
            val realSubstitutions: MutableList<String> = ArrayList(possibleSubstitutions.size + 1)
            for (substitution in possibleSubstitutions) {
                if (headers.contains(substitution)) {
                    realSubstitutions.add(substitution)
                }
            }
            return if (realSubstitutions.isEmpty()) null else realSubstitutions
        }
    }
}