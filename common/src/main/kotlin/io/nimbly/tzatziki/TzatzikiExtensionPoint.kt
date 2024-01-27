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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import javax.swing.Icon

interface TzatzikiExtensionPoint {

    fun isDeprecated(element: PsiElement): Boolean
    fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean
    fun findBreakpoint(source: PsiElement, stepDefinitions: List<AbstractStepDefinition>): TzBreakpoint?
    fun initBreakpointListener(project: Project)
}

object Tzatziki {
    operator fun invoke(): ExtensionPointName<TzatzikiExtensionPoint> =
        ExtensionPointName.create("io.nimbly.tzatziki.io.nimbly.tzatziki.main")
}

abstract class TzBreakpoint(
    val navigatable: Navigatable,
    val tooltip: String,
    val icon: Icon,
    val file: PsiFile,
    val targets: List<PsiElement>) : UserDataHolder
