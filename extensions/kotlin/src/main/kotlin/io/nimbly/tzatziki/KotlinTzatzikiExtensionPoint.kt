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
import com.intellij.xdebugger.breakpoints.XBreakpoint
import io.nimbly.tzatziki.util.findUsages
import io.nimbly.tzatziki.util.getDocumentEndLine
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.getFile
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class KotlinTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {
        return element is PsiMethod && element.isDeprecated
    }

    /**
     * Let's do it using java extension
     */
    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return false
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

        val fcts = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
            ?: return null

        val cucumberAnnotation = fcts.annotationEntries
            .find { it.resolveToDescriptorIfAny(BodyResolveMode.PARTIAL_NO_ADDITIONAL)?.fqName?.asString()?.startsWith("io.cucumber.java") == true }
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

        val l =
            if (start + 1 <= end)
                start + 1
            else
                start

        return m to l
    }
}
