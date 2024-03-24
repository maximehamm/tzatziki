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

package io.nimbly.tzatziki.breakpoints

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.nullIfEmpty
import io.nimbly.tzatziki.util.updatePresentation
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

class TzBreakpointSyncInspection : GherkinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

        return object : GherkinElementVisitor() {

            override fun visitStep(step: GherkinStep) {

                super.visitStep(step)
                if (!TOGGLE_CUCUMBER_PL)
                    return

                val stepLine = step.getDocumentLine()
                    ?: return

                // Look for gherkin breakpoints
                val reference = step.references
                    .filterIsInstance<CucumberStepReference>()
                    .firstOrNull()
                val gherkinBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
                    .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                    .filter { it.sourcePosition?.line == stepLine }
                    .nullIfEmpty()

                // Look for code breakpoints
                val codeElement = reference?.resolveToDefinition()?.element
                val codeBreakpoints =
                    Tzatziki().extensionList.firstNotNullOfOrNull {
                    it.findStepsAndBreakpoints(
                        codeElement?.containingFile?.virtualFile,
                        codeElement?.textOffset
                    )
                }?.second?.nullIfEmpty()

                // Compare
                if (gherkinBreakpoints != null && codeBreakpoints == null) {

                    // Disable breakpoint since reference is lost !
                    gherkinBreakpoints.forEach { it.isEnabled = false }
                }
                else if (gherkinBreakpoints == null && codeBreakpoints != null) {

                    // Create missing gherkin breakpoint
                    XDebuggerUtil.getInstance().toggleLineBreakpoint(
                        step.project,
                        step.containingFile.virtualFile,
                        stepLine
                    )
                    step.updatePresentation(codeBreakpoints)
                }
                else if (codeBreakpoints!=null) {
                    // step.updatePresentation(codeBreakpoints)
                }
            }
        }
    }
}