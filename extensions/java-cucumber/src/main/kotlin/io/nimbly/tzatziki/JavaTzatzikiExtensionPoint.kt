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
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.java.steps.AbstractJavaStepDefinition
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class JavaTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {
        return element is PsiMethod
                && (element.isDeprecated || element.containingClass?.isDeprecated == true)
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return null != stepDefinitions.firstOrNull { it is AbstractJavaStepDefinition }
    }

    override fun findStepsAndBreakpoints(vfile: VirtualFile?, offset: Int?): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {

        vfile ?: return null
        offset ?: return null

        val project = ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .firstOrNull() { vfile.getFile(it) != null }
            ?: return null

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

        val method = stepDefinitions
            .mapNotNull { it.element }
            .filterIsInstance<PsiMethod>()
            .firstOrNull()
            ?: return null

        val m = method
        val start = m.getDocumentLine() ?:return null
        val end = m.getDocumentEndLine() ?:return null

        var line = start
        val xutil = XDebuggerUtil.getInstance()
        for (l in start + 1..end) {
            if (xutil.canPutBreakpointAt(m.project, m.containingFile.virtualFile, l)) {
                line = l
                break
            }
        }

        return m to line
    }
}