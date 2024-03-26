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
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

class TzBreakpointSyncInspection : GherkinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

        return object : GherkinElementVisitor() {

            override fun visitFile(file: PsiFile) {
                // Remove orphan breakpoints
                XDebuggerManager.getInstance(file.project)
                    .breakpointManager
                    .allBreakpoints
                    .filter { it.sourcePosition?.file == file.virtualFile }
                    .forEach { breakpoint ->

                        val line = breakpoint.sourcePosition?.line ?: return@forEach
                        val range = file.getDocument()?.getLineRange(line) ?: return@forEach
                        if (file.findElementsOfTypeInRange(range, GherkinStep::class.java, GherkinTableRow::class.java).isNotEmpty())
                            return@forEach

                        DumbService.getInstance(file.project).smartInvokeLater {
                            XDebuggerUtil.getInstance().removeBreakpoint(file.project, breakpoint)
                        }
                    }
            }


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
                val gherkinBreakpoints = XDebuggerManager.getInstance(step.project)
                    .breakpointManager
                    .allBreakpoints
                    .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                    .filter { it.sourcePosition?.line == stepLine }
                    .nullIfEmpty()

                // Look for code breakpoints
                val stepDefinitions = reference?.resolveToDefinition()
                val codeElement = stepDefinitions?.element
                val codeBreakpoints =
                    Tzatziki().extensionList.firstNotNullOfOrNull {
                    it.findStepsAndBreakpoints(
                        codeElement?.containingFile?.virtualFile,
                        codeElement?.textOffset
                    )
                }?.second?.nullIfEmpty()

                // Mark all code breakpoint
                codeBreakpoints?.forEach {
                    if (it.conditionExpression == null)
                        it.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
                }

                // Compare
                if (gherkinBreakpoints != null && codeBreakpoints == null && stepDefinitions != null) {

                    // Restore breakpoint since reference is lost !
                    val elt = Tzatziki().extensionList.firstNotNullOfOrNull {
                        it.findBestPositionToAddBreakpoint(listOf(stepDefinitions))
                    } ?: return
                    toggleCodeBreakpoint(elt, step.project)

                }
                else if (gherkinBreakpoints == null && codeBreakpoints != null) {

                    // Create missing gherkin breakpoint
                    // TODO Do it only if code step has only one reference !
                    // step.toggleGherkinBreakpoint(stepLine)
                }
                else if (codeBreakpoints!=null) {

                    // step.updatePresentation(codeBreakpoints)
                }

            }
        }
    }
}