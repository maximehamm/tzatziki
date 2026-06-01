/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.openapi.vfs.VirtualFile
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
import io.nimbly.tzatziki.util.findCucumberStepDefinitions
import io.nimbly.tzatziki.util.findProject
import io.nimbly.tzatziki.util.getDocument
import io.nimbly.tzatziki.util.getFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

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
