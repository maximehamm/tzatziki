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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class KotlinTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {
        return element is PsiMethod && element.isDeprecated
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return false
    }

    override fun findBreakpoint(source: PsiElement, stepDefinitions: List<AbstractStepDefinition>): TzBreakpoint? {
        return null
    }

    override fun initBreakpointListener(project: Project) = Unit
}