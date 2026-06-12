/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import io.nimbly.tzatziki.rename.StepPatternInfo
import io.nimbly.tzatziki.util.findCucumberStepDefinitions
import io.nimbly.tzatziki.util.findProject
import io.nimbly.tzatziki.util.getDocument
import io.nimbly.tzatziki.util.getFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * Rebuild a Python string literal's source text with a new pattern body, preserving the original
 * prefix (`r`/`u`/`b`/`f`) and quote, escaping the quote (and `\` unless it is a raw `r'…'` string).
 * Pure (no PSI) so it is unit-testable despite the platform blocking Python PSI in light fixtures.
 * Returns `null` if [originalText] has no recognisable quote.
 */
fun pyRewrittenLiteralText(originalText: String, newPattern: String): String? {
    val quoteIdx = originalText.indexOfFirst { it == '\'' || it == '"' }
    if (quoteIdx < 0) return null
    val prefix = originalText.substring(0, quoteIdx)
    val quote = originalText[quoteIdx]
    val isRaw = prefix.contains('r', ignoreCase = true)
    val body = buildString {
        for (c in newPattern) {
            if (c == quote || (c == '\\' && !isRaw)) append('\\')   // raw string: no backslash escaping
            append(c)
        }
    }
    return "$prefix$quote$body$quote"
}

/**
 * Python / behave implementation of [TzatzikiExtensionPoint] — drives the
 * Gherkin ↔ step-def **breakpoint synchronisation** for `.py` step definitions,
 * mirroring [JavaTzatzikiExtensionPoint] / [JsTzatzikiExtensionPoint].
 *
 * A behave step definition is a top-level function decorated with
 * `@given/@when/@then/@step`; Cucumber+ syncs a breakpoint on its first body
 * statement when the matching Gherkin step is breakpointed (and vice-versa).
 */
class PyTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    private val behaveDecorators = setOf("given", "when", "then", "step", "and", "but")

    // --- synchronised step rename (#8) --------------------------------------

    override fun getStepPattern(stepDefElement: PsiElement): StepPatternInfo? {
        val raw = patternLiteral(stepDefElement)?.stringValue ?: return null
        return StepPatternInfo.of(raw)   // behave: parse-style `{name}` (BRACED) or regex (`^…$`)
    }

    override fun rewriteStepPattern(stepDefElement: PsiElement, newPattern: String): Boolean {
        val literal = patternLiteral(stepDefElement) ?: return false
        val newText = pyRewrittenLiteralText(literal.text, newPattern) ?: return false
        val doc = PsiDocumentManager.getInstance(literal.project).getDocument(literal.containingFile) ?: return false
        doc.replaceString(literal.textRange.startOffset, literal.textRange.endOffset, newText)
        PsiDocumentManager.getInstance(literal.project).commitDocument(doc)
        return true
    }

    /** The string literal of the `@given/@when/@then(...)` decorator on the behave step function. */
    private fun patternLiteral(element: PsiElement): PyStringLiteralExpression? {
        (element as? PyStringLiteralExpression)?.let { return it }
        val fct = element as? PyFunction ?: PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
        val deco = fct.decoratorList?.decorators?.firstOrNull { it.name?.lowercase() in behaveDecorators } ?: return null
        return PsiTreeUtil.findChildOfType(deco, PyStringLiteralExpression::class.java)
    }

    override fun isDeprecated(element: PsiElement): Boolean = false

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean =
        stepDefinitions.any { it.element?.containingFile is PyFile }

    override fun findStepsAndBreakpoints(
        vfile: VirtualFile?,
        offset: Int?,
    ): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {
        vfile ?: return null
        offset ?: return null
        if (vfile.extension?.lowercase() != "py") return null

        val project = vfile.findProject() ?: return null
        val file = vfile.getFile(project) ?: return null
        val element = file.findElementAt(offset) ?: return null
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
        if (!isBehaveStep(function)) return null

        val steps = gherkinStepsFor(function)

        val funcRange = function.textRange
        val funcVfile = function.containingFile.originalFile.virtualFile
        val breakpoints = XDebuggerManager.getInstance(project).breakpointManager
            .allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter {
                val sp = it.sourcePosition ?: return@filter false
                sp.file == funcVfile && funcRange.contains(sp.offset)
            }

        return steps to breakpoints
    }

    override fun findBestPositionToAddBreakpoint(
        stepDefinitions: List<AbstractStepDefinition>,
    ): Pair<PsiElement, Int>? {
        val function = stepDefinitions
            .mapNotNull { it.element }
            .mapNotNull { it as? PyFunction ?: PsiTreeUtil.getParentOfType(it, PyFunction::class.java) }
            .firstOrNull()
            ?: return null

        // First executable statement of the function body.
        val firstStatement = function.statementList.statements.firstOrNull() ?: return null
        val doc = function.containingFile.getDocument() ?: return null
        val line = doc.getLineNumber(firstStatement.textOffset)
        return function to line
    }

    // No promoteToCucumberType override: Python step-def breakpoints stay the native
    // `python-line` type (pydevd only installs that one). The default returns false,
    // so a user breakpoint on a step-def body keeps its native type and still fires,
    // while the position-based sync still mirrors it to the Gherkin side.

    // -- helpers -------------------------------------------------------------

    private fun isBehaveStep(function: PyFunction): Boolean {
        val decorators = function.decoratorList?.decorators ?: return false
        return decorators.any { it.name?.lowercase() in behaveDecorators }
    }

    /** Reverse-search: enumerate every `.feature` file and keep the Gherkin steps
     *  whose resolved step definition lands inside [function]. */
    private fun gherkinStepsFor(function: PyFunction): List<GherkinStep> {
        val project = function.project
        val funcRange = function.textRange
        val funcVfile = function.containingFile.virtualFile
        val result = mutableListOf<GherkinStep>()
        val gherkinFiles = FileTypeIndex.getFiles(
            GherkinFileType.INSTANCE, GlobalSearchScope.projectScope(project),
        )
        val psiManager = PsiManager.getInstance(project)
        for (gvfile in gherkinFiles) {
            val gpsiFile = psiManager.findFile(gvfile) ?: continue
            PsiTreeUtil.findChildrenOfType(gpsiFile, GherkinStep::class.java).forEach { step ->
                val matched = step.findCucumberStepDefinitions().any { d ->
                    val e = d.element ?: return@any false
                    e.containingFile?.virtualFile == funcVfile && funcRange.contains(e.textOffset)
                }
                if (matched) result += step
            }
        }
        return result
    }
}
