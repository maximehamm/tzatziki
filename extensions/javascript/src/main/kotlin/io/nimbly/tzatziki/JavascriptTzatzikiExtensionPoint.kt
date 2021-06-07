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

import com.intellij.lang.javascript.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class JavascriptTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {
        if (element !is JSLiteralExpression)
            return false

        val mainFunction = element.parentOfType<JSFunctionExpression>()
        if (mainFunction !=null && mainFunction.isDeprecated)
            return true

        val args = element.parent
        if (args !is JSArgumentList)
            return false

        val fct = args.arguments.filterIsInstance<JSFunctionExpression>().firstOrNull()
        if (fct != null)
            return fct.isDeprecated

        val ref = args.arguments.filterIsInstance<JSReferenceExpression>().firstOrNull()?.resolve()
            ?: return false
        if (ref is JSFunction)
            return ref.isDeprecated

        return false
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return true
    }
}