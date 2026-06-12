/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

package io.nimbly.tzatziki

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import io.nimbly.tzatziki.rename.StepPatternInfo
import io.nimbly.tzatziki.util.*
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class KotlinTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    // --- synchronised step rename (#8), Kotlin step definitions -------------

    override fun getStepPattern(stepDefElement: PsiElement): StepPatternInfo? {
        val raw = ktStringValue(cucumberAnnotationStringTemplate(stepDefElement) ?: return null) ?: return null
        return StepPatternInfo.of(raw)
    }

    override fun rewriteStepPattern(stepDefElement: PsiElement, newPattern: String): Boolean {
        val template = cucumberAnnotationStringTemplate(stepDefElement) ?: return false
        val newExpr = KtPsiFactory(template.project).createExpression("\"" + escapeKotlinString(newPattern) + "\"")
        template.replace(newExpr)
        return true
    }

    /** The `@Given("…")`-style string-literal argument of the cucumber annotation on the Kotlin
     *  step-definition function ([element] may be the function, its light method, or the annotation). */
    private fun cucumberAnnotationStringTemplate(element: PsiElement): KtStringTemplateExpression? {
        val nav = element.navigationElement
        val fct = nav as? KtNamedFunction
            ?: PsiTreeUtil.getParentOfType(nav, KtNamedFunction::class.java)
            ?: element as? KtNamedFunction
            ?: PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
            ?: return null
        val anno = fct.annotationEntries.find { it.isCucumberJavaAnnotation() } ?: return null
        return anno.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
    }

    /** The literal value of a Kotlin string template, or `null` if it contains interpolation. */
    private fun ktStringValue(template: KtStringTemplateExpression): String? = buildString {
        for (entry in template.entries) when (entry) {
            is KtLiteralStringTemplateEntry -> append(entry.text)
            is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
            else -> return null   // ${'$'}{…} / $name interpolation → not a static pattern
        }
    }

    /** Escape for a Kotlin double-quoted string literal — note `$` must be escaped (interpolation). */
    private fun escapeKotlinString(s: String): String = buildString {
        for (c in s) { if (c == '\\' || c == '"' || c == '$') append('\\'); append(c) }
    }

    override fun isDeprecated(element: PsiElement): Boolean {
        // In recent cucumber-java versions definition.element is the @Given/@When/@Then
        // annotation (light Kotlin wrapper for Kotlin sources).
        // Light wrappers cache isDeprecated, so prefer reading the Kotlin source directly.
        val nav = element.navigationElement
        val ktFunction = nav as? KtNamedFunction
            ?: PsiTreeUtil.getParentOfType(nav, KtNamedFunction::class.java)
        if (ktFunction != null) {
            if (ktFunction.hasDeprecatedAnnotation()) return true
            val owner = ktFunction.containingClassOrObject
            return owner != null && owner.hasDeprecatedAnnotation()
        }
        // Java fallback
        val method = element as? PsiMethod
            ?: PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            ?: return false
        return method.isDeprecated || method.containingClass?.isDeprecated == true
    }

    private fun KtAnnotated.hasDeprecatedAnnotation(): Boolean =
        annotationEntries.any { it.shortName?.asString() == "Deprecated" }

    /**
     * Let's do it using java extension
     */
    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return false
    }

    override fun findStepsAndBreakpoints(vfile: VirtualFile?, offset: Int?): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {

        vfile ?: return null
        offset ?: return null

        val project = vfile.findProject() ?: return null
        val file = vfile.getFile(project) ?: return null
        val element = file.findElementAt(offset) ?: return null

        val fcts = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
            ?: return null

        val cucumberAnnotation = fcts.annotationEntries.find { it.isCucumberJavaAnnotation() }
        if (cucumberAnnotation == null)
            return null

        val stepReferences = findUsages(fcts)
        val steps = stepReferences.map { it.element }.filterIsInstance<GherkinStep>()

        val allBreakpoints = DebuggerManagerEx.getInstanceEx(project)
            .breakpointManager
            .breakpoints
            .filter { fcts.textRange.contains( it.xBreakpoint.sourcePosition?.offset ?: -1) }
            .filter { it.evaluationElement?.containingFile?.originalFile?.virtualFile == fcts.containingFile.originalFile.virtualFile }
            .map { it.xBreakpoint }

        return steps to allBreakpoints
    }

    override fun findBestPositionToAddBreakpoint(stepDefinitions: List<AbstractStepDefinition>): Pair<PsiElement, Int>? {

        val fcts = stepDefinitions
            .mapNotNull { it.element }
            .filterIsInstance<KtNamedFunction>()

        if (fcts.isEmpty())
            return null

        val m = fcts.firstOrNull() ?: return null
        val start = m.getDocumentLine() ?:return null
        val end = m.getDocumentEndLine() ?:return null

        // Walk the function body looking for an executable line — that's where the JVM
        // line table will have an entry our breakpoint can match against a class prepare.
        val xutil = XDebuggerUtil.getInstance()
        var line = -1
        for (l in start + 1..end) {
            if (xutil.canPutBreakpointAt(m.project, m.containingFile.virtualFile, l)) {
                line = l
                break
            }
        }
        // Fallback for empty / single-statement bodies that canPutBreakpointAt rejects on
        // every body line: target the CLOSING brace (`}` line) — Kotlin's compiler attaches
        // the line number of the closing brace to the implicit RETURN, so a JDI breakpoint
        // there fires when the function exits. The previous fallback to the `fun`
        // declaration line was silent because that line has no bytecode line entry.
        if (line < 0) line = if (end > start) end else start

        return m to line
    }
}
