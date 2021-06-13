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
    }

    override fun initBreakpointListener(project: Project) = Unit
}