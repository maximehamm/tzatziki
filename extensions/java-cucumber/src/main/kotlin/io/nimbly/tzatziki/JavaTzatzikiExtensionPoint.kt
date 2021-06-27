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

package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import io.nimbly.tzatziki.util.collectReferences
import io.nimbly.tzatziki.util.getDocument
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.getFile
import org.jetbrains.plugins.cucumber.java.steps.AbstractJavaStepDefinition
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import org.jetbrains.plugins.cucumber.steps.search.CucumberStepSearchUtil.restrictScopeToGherkinFiles

class JavaTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {
        return element is PsiMethod
                && (element.isDeprecated || element.containingClass?.isDeprecated == true)
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return null != stepDefinitions.firstOrNull { it is AbstractJavaStepDefinition }
    }

    override fun findBreakpoint(source: PsiElement, stepDefinitions: List<AbstractStepDefinition>): TzBreakpoint? {

        val methods = stepDefinitions
            .mapNotNull { it.element }
            .filterIsInstance<PsiMethod>()

        if (methods.isEmpty())
            return null

        val breakpoints = DebuggerManagerEx.getInstanceEx(methods.first().project)
            .breakpointManager
            .breakpoints

        var tzbreakpoint: TzBreakpoint? = null
        methods
            .filter { it.getDocument() != null }
            .filter { it.getDocumentLine() != null }
            .forEach { method ->

                breakpoints
                    .filter { method.textRange.contains( it.xBreakpoint.sourcePosition?.offset ?: -1) }
                    .forEach { breakpoint ->

                        val tooltip = breakpoint.displayName
                        val navigatable = breakpoint.xBreakpoint.navigatable ?: method
                        val icon =
                            if (breakpoint.xBreakpoint.isEnabled) breakpoint.xBreakpoint.type.enabledIcon
                            else breakpoint.xBreakpoint.type.disabledIcon

                        tzbreakpoint = TzBreakpoint(navigatable, tooltip, icon,
                            listOfNotNull(source, method, breakpoint.evaluationElement))

                        if (breakpoint.xBreakpoint.isEnabled)
                            return tzbreakpoint
                    }
            }

        return tzbreakpoint
    }

    override fun initBreakpointListener(project: Project) {

        fun refresh(breakpoint: XBreakpoint<*>) {

            val sourcePosition = breakpoint.sourcePosition ?: return

            val vfile = sourcePosition.file
            if (!vfile.isValid) return
            val file = vfile.getFile(project) as? PsiJavaFile ?: return

            val element = file.findElementAt(sourcePosition.offset) ?: return
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return

            val scope = restrictScopeToGherkinFiles(GlobalSearchScope.projectScope(project))
            val references = method.collectReferences(scope)
            references
                .asSequence()
                .filterIsInstance<CucumberStepReference>()
                .map { it.element }
                .filterIsInstance<GherkinStep>()
                .map { it.containingFile }
                .toSet()
                .forEach {
                    DaemonCodeAnalyzer.getInstance(project).restart(it)
            }

        }

        project.messageBus
            .connect()
            .subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
                override fun breakpointChanged(breakpoint: XBreakpoint<*>) = refresh(breakpoint)
                override fun breakpointAdded(breakpoint: XBreakpoint<*>) = refresh(breakpoint)
                override fun breakpointRemoved(breakpoint: XBreakpoint<*>) = refresh(breakpoint)
                override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) = Unit
            })
    }

}