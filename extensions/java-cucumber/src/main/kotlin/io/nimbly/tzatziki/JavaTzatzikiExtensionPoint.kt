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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import io.nimbly.tzatziki.rename.StepPatternInfo
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.java.steps.AbstractJavaStepDefinition
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class JavaTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    // --- synchronised step rename (#8) --------------------------------------

    override fun getStepPattern(stepDefElement: PsiElement): StepPatternInfo? {
        if (isKotlinSource(stepDefElement)) return null
        val raw = (cucumberAnnotationValue(stepDefElement) as? PsiLiteralExpression)?.value as? String ?: return null
        return StepPatternInfo.of(raw)
    }

    override fun rewriteStepPattern(stepDefElement: PsiElement, newPattern: String): Boolean {
        if (isKotlinSource(stepDefElement)) return false
        val value = cucumberAnnotationValue(stepDefElement) as? PsiLiteralExpression ?: return false
        val factory = JavaPsiFacade.getElementFactory(value.project)
        val newLiteral = factory.createExpressionFromText("\"" + escapeJavaString(newPattern) + "\"", value)
        value.replace(newLiteral)
        return true
    }

    /** A Kotlin step definition resolves here too (via a light `PsiMethod`), and this EP CAN read its
     *  pattern — but `value.replace(...)` on the light element does NOT touch the `.kt` source (silent
     *  no-op). Since EPs are tried in order (this one before [io.nimbly.tzatziki.KotlinTzatzikiExtensionPoint]),
     *  we must decline Kotlin sources so the Kotlin EP — which rewrites the real source — handles them. */
    private fun isKotlinSource(element: PsiElement): Boolean =
        element.navigationElement.containingFile?.language?.id == "kotlin"

    /** The `value` attribute element of the `io.cucumber.java.*` annotation on the step-def method
     *  ([element] may be the method itself or the annotation, per the cucumber-java version). */
    private fun cucumberAnnotationValue(element: PsiElement): PsiAnnotationMemberValue? {
        val method = element as? PsiMethod ?: PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return null
        val annotation = method.annotations.find {
            it.resolveAnnotationType()?.qualifiedName?.startsWith("io.cucumber.java") == true
        } ?: return null
        return annotation.findAttributeValue("value")
    }

    private fun escapeJavaString(s: String): String =
        buildString { for (c in s) { if (c == '\\' || c == '"') append('\\'); append(c) } }

    override fun isDeprecated(element: PsiElement): Boolean {
        // In recent cucumber-java versions definition.element is the @Given/@When/@Then
        // annotation rather than the PsiMethod, so resolve to the enclosing method first.
        val method = element as? PsiMethod
            ?: PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            ?: return false
        // Skip Kotlin light wrappers — the Kotlin extension reads the Kotlin source
        // directly to avoid stale cached isDeprecated on the wrapper.
        val fileType = method.containingFile?.fileType?.name.orEmpty()
        if (fileType.equals("kotlin", ignoreCase = true)) return false
        return method.isDeprecated || method.containingClass?.isDeprecated == true
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return null != stepDefinitions.firstOrNull { it is AbstractJavaStepDefinition }
    }

    override fun findStepsAndBreakpoints(vfile: VirtualFile?, offset: Int?): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {

        vfile ?: return null
        offset ?: return null

        val project = vfile.findProject() ?: return null
        val file = vfile.getFile(project) ?: return null
        val element = file.findElementAt(offset) ?: return null

        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            ?: return null

        val cucumberAnnotation = method.annotations
            .find { it.resolveAnnotationType()?.qualifiedName?.startsWith("io.cucumber.java") == true }
        if (cucumberAnnotation == null)
            return null

        val stepReferences = findStepUsages(method)
        val steps = stepReferences.map { it.element }.filterIsInstance<GherkinStep>()

        val allBreakpoints = DebuggerManagerEx.getInstanceEx(project)
            .breakpointManager
            .breakpoints
            .filter { method.textRange.contains( it.xBreakpoint.sourcePosition?.offset ?: -1) }
            .filter { it.evaluationElement?.containingFile?.originalFile?.virtualFile == method.containingFile.originalFile.virtualFile }
            .map { it.xBreakpoint }

        return steps to allBreakpoints

    }

    override fun findBestPositionToAddBreakpoint(stepDefinitions: List<AbstractStepDefinition>): Pair<PsiElement, Int>? {

        // In recent cucumber-java versions, AbstractStepDefinition.getElement() returns the
        // @Given/@When/@Then annotation rather than the method itself. Walk up if needed.
        val method = stepDefinitions
            .mapNotNull { it.element }
            .mapNotNull { elt ->
                elt as? PsiMethod
                    ?: PsiTreeUtil.getParentOfType(elt, PsiMethod::class.java)
            }
            .firstOrNull()
            ?: return null

        val m = method
        val end = m.getDocumentEndLine() ?:return null

        // Start scanning AFTER the opening `{` of the method body — not at the method
        // declaration line. For Kotlin, KtLightMethod.getDocumentLine() returns the line
        // of the @Annotation (or `fun` declaration), and canPutBreakpointAt happily
        // returns true for the `fun foo() {` line itself even though that line has no
        // bytecode entry. A breakpoint set there is silently dropped by the JVM and the
        // user sees no break. Fall back to the method declaration line only when no
        // executable body line was found at all.
        val body = m.body
        val bodyStartLine = body?.lBrace?.getDocumentLine()
            ?: m.getDocumentLine()
            ?: return null

        val xutil = XDebuggerUtil.getInstance()
        var line = -1
        for (l in bodyStartLine + 1..end) {
            if (xutil.canPutBreakpointAt(m.project, m.containingFile.virtualFile, l)) {
                line = l
                break
            }
        }
        // Empty / single-statement bodies that canPutBreakpointAt rejects on every body
        // line: target the CLOSING brace (`}`). Both Java and Kotlin compilers attach the
        // line of the closing brace to the implicit RETURN, so a JDI breakpoint there
        // fires when the method exits.
        if (line < 0) line = end

        return m to line
    }
}