/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class ScalaTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {

        val parent = element.parent
        if (parent !is ScMethodCall)
            return false

        val clazz = parent.parentOfType<ScClass>()
        if (clazz !=null && clazz.isDeprecated)
            return true

        val visitor = MyVisitor()
        parent.acceptScala(visitor)
        return visitor.deprecated
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

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return false
    }

    override fun findBreakpoint(source: PsiElement, stepDefinitions: List<AbstractStepDefinition>): TzBreakpoint? {

        return null
//        val methods = stepDefinitions
//            .mapNotNull { it.element }
//            .filterIsInstance<ScMethodCall>()
//
//        if (methods.isEmpty())
//            return null
//
//        val breakpoints = DebuggerManagerEx.getInstanceEx(methods.first().project)
//            .breakpointManager
//            .breakpoints
//            .filter { it.xBreakpoint.sourcePosition?.offset != null }
//
//        var tzbreakpoint: TzBreakpoint? = null
//        methods
//            .filter { it.getDocument() != null }
//            .filter { it.getDocumentLine() != null }
//            .forEach { method ->
//
//                breakpoints
//                    .filter { method.parent.textRange.contains(it.xBreakpoint.sourcePosition?.offset ?: -1) }
//                    .forEach { breakpoint ->
//
//                        val tooltip = breakpoint.displayName
//                        val navigatable = breakpoint.xBreakpoint.navigatable ?: method.navigationElement as Navigatable
//                        val icon =
//                            if (breakpoint.xBreakpoint.isEnabled) breakpoint.xBreakpoint.type.enabledIcon
//                            else breakpoint.xBreakpoint.type.disabledIcon
//
//                        tzbreakpoint = TzBreakpoint(navigatable, tooltip, icon,
//                            listOfNotNull(source, method, breakpoint.evaluationElement))
//
//                        if (breakpoint.xBreakpoint.isEnabled)
//                            return tzbreakpoint
//                    }
//            }
//
//        return tzbreakpoint
    }

    override fun initBreakpointListener(project: Project) {

//        fun refresh(breakpoint: XBreakpoint<*>) {
//
//            val sourcePosition = breakpoint.sourcePosition ?: return
//
//            val vfile = sourcePosition.file
//            if (!vfile.isValid) return
//            val file = vfile.getFile(project) ?: return
//            if (file !is ScalaFile) return
//
//            val element = file.findElementAt(sourcePosition.offset) ?: return
//            val method = PsiTreeUtil.getParentOfType(element, ScMethodCall::class.java) ?: return
//
//            val usagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
//            val handler = usagesManager.getFindUsagesHandler(method, false) ?: return
//
//            val references = mutableListOf<PsiReference>()
//            handler.processElementUsages(method.parent, {
//                val ref = it.reference
//                if (ref !=null)
//                    references.add(ref)
//                true
//            }, handler.findUsagesOptions)
//
//            references
//                .asSequence()
//                .filterIsInstance<CucumberStepReference>()
//                .map { it.element }
//                .filterIsInstance<GherkinStep>()
//                .map { it.containingFile }
//                .toSet()
//                .forEach {
//                    DaemonCodeAnalyzer.getInstance(project).restart(it)
//                }
//
//        }
//
//        project.messageBus
//            .connect()
//            .subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
//                override fun breakpointChanged(breakpoint: XBreakpoint<*>) = refresh(breakpoint)
//                override fun breakpointAdded(breakpoint: XBreakpoint<*>) = refresh(breakpoint)
//                override fun breakpointRemoved(breakpoint: XBreakpoint<*>) = refresh(breakpoint)
//                override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) = Unit
//            })
    }
}