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
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.*
import com.intellij.xdebugger.breakpoints.XBreakpoint
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class ScalaTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    companion object {
        private val CacheKey: Key<CachedValue<List<GherkinStep>>> = Key.create(Companion::CacheKey.javaClass.simpleName)
    }

    override fun isDeprecated(element: PsiElement): Boolean {

        val parent = element.parent
        if (parent !is ScMethodCall)
            return false

        val clazz = parent.parentOfTypeIs<ScClass>()
        if (clazz !=null && clazz.isDeprecated)
            return true

        val visitor = MyVisitor()
        parent.acceptScala(visitor)
        return visitor.deprecated
    }

    override fun findStepsAndBreakpoints(vfile: VirtualFile?, offset: Int?): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {

        vfile ?: return null
        offset ?: return null

        val project = ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .firstOrNull() { vfile.getFile(it) != null }
            ?: return null

        val file = vfile.getFile(project) ?: return null
        if (file !is ScalaFile) return null

        val element = file.findElementAt(offset) ?: return null
        val method = PsiTreeUtil.getParentOfType(element, ScMethodCall::class.java)
            ?: return null

        // TODO Try to use CucumberUtil().findGherkinReferencesToElement ?
        val allSteps = mutableSetOf<GherkinStep>()
        FilenameIndex
            .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, project.getGherkinScope())
            .map { it.getFile(project) }
            .filterIsInstance<GherkinFile>()
            .forEach { f ->
                val steps = CachedValuesManager.getCachedValue(f, CacheKey) {

                    val steps = f.features
                        .flatMap { feature -> feature.scenarios.toList() }
                        .flatMap { scenario -> scenario.steps.toList() }

                    CachedValueProvider.Result.create(
                        steps,
                        PsiModificationTracker.MODIFICATION_COUNT, f
                    )
                }
                allSteps.addAll(steps)
            }

        val steps = allSteps.filter { it.findCucumberStepReference()?.resolve() == method.firstChild }

        val allBreakpoints = DebuggerManagerEx.getInstanceEx(project)
            .breakpointManager
            .breakpoints
            .filter { method.textRange.contains( it.xBreakpoint.sourcePosition?.offset ?: -1) }
            .filter { it.evaluationElement?.containingFile?.originalFile?.virtualFile == method.containingFile.originalFile.virtualFile }
            .map { it.xBreakpoint }

        return steps to allBreakpoints
    }

    override fun findBestPositionToAddBreakpoint(stepDefinitions: List<AbstractStepDefinition>): Pair<PsiElement, Int>? {

        val fcts = stepDefinitions
            .mapNotNull { it.element }
            .filterIsInstance<ScMethodCall>()

        if (fcts.isEmpty())
            return null

        val m = fcts.firstOrNull()?.parent ?: return null
        val l = m.getDocumentLine() ?: return null
        return m to l+1
    }

    class MyVisitor : ScalaRecursiveElementVisitor() {

        var deprecated = false

        override fun visitFunction(func: ScFunction) {
            if (func.isDeprecated) {
                deprecated = true
            }
            super.visitFunction(func)
        }

        override fun visitElement(element: PsiElement) {
            if (element is PsiDocCommentOwner
                && element.isDeprecated){
                deprecated = true
            }
            super.visitElement(element)
        }
    }

    /**
     * Let's do it using java extension
     */
    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return false
    }

}