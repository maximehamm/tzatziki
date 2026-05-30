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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

interface TzatzikiExtensionPoint {

    fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean
    fun isDeprecated(element: PsiElement): Boolean

    fun findBestPositionToAddBreakpoint(stepDefinitions: List<AbstractStepDefinition>): Pair<PsiElement, Int>?
    fun findStepsAndBreakpoints(vfile: VirtualFile?, offset: Int?): Pair<List<GherkinStep>, List<XBreakpoint<*>>>?

    /**
     * "Promotes" a user-created language-native line breakpoint that sits on a
     * Cucumber step-def body line into the language-specific Cucumber+ breakpoint
     * type (e.g. `TzCucumberCodeBreakpointType` for JVM, `TzCucumberJsBreakpointType`
     * for JS/TS). Returns `true` if this extension owns the file and performed the
     * promotion (or decided it shouldn't be promoted) — in which case the caller
     * stops iterating. The default `false` means "I don't handle this file type".
     */
    fun promoteToCucumberType(breakpoint: XLineBreakpoint<*>, project: Project): Boolean = false
}

object Tzatziki {

    operator fun invoke(): ExtensionPointName<TzatzikiExtensionPoint> =
        ExtensionPointName.create("io.nimbly.tzatziki.io.nimbly.tzatziki.main")

    fun findSteps(vfile: VirtualFile?, offset: Int?): List<GherkinStep> {
        return findStepsAndBreakpoints(vfile, offset)?.first ?: listOf()
    }

    fun findBreakpoints(vfile: VirtualFile?, offset: Int?): List<XBreakpoint<*>> {
        return findStepsAndBreakpoints(vfile, offset)?.second ?: listOf()
    }

    fun findStepsAndBreakpoints(vfile: VirtualFile?, offset: Int?): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {
        return invoke().extensionList.firstNotNullOfOrNull {
            it.findStepsAndBreakpoints(vfile, offset)
        }
    }

}