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
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import javax.swing.Icon

class TzBreakpointSyncInspection : GherkinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

        return object : GherkinElementVisitor() {

            /*
            override fun visitFile(file: PsiFile) {

                // Look for orphan breakpoint that no more belongs to a step
                val gherkinBreakpoints = XDebuggerManager.getInstance(file.project).breakpointManager.allBreakpoints
                    .filter { it.sourcePosition?.file == file.virtualFile }
                    .nullIfEmpty()
                    ?: return

                XDebuggerManager.getInstance(file.project).breakpointManager.allBreakpoints
                    .filter { it.sourcePosition?.file == file.virtualFile }
                    .forEach { p ->
                        val line = file.getDocumentLine() ?: return@forEach
                        val step = findStep(file.virtualFile, file.project, line)
                        if (step == null) {
                            ApplicationManager.getApplication().invokeLater {
                                ApplicationManager.getApplication().runWriteAction {
                                    gherkinBreakpoints.forEach {
                                        XDebuggerManager.getInstance(file.project).breakpointManager.removeBreakpoint(p)
                                    }
                                }
                            }
                        }
                }
            }
            */

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

                // Mark all code breakpoint
                codeBreakpoints?.forEach {
                    if (it.conditionExpression == null)
                        it.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
                }

                // Compare
                if (gherkinBreakpoints != null && codeBreakpoints == null) {

                    // Drop breakpoint since reference is lost !
                    /*
                    ApplicationManager.getApplication().invokeLater {
                        ApplicationManager.getApplication().runWriteAction {
                            gherkinBreakpoints.forEach {
                                XDebuggerManager.getInstance(step.project).breakpointManager.removeBreakpoint(it)
                            }
                        }
                    }*/
                }
                else if (gherkinBreakpoints == null && codeBreakpoints != null) {

                    // Create missing gherkin breakpoint
                    step.updatePresentation(codeBreakpoints)
                }
                else if (codeBreakpoints!=null) {
                    // step.updatePresentation(codeBreakpoints)
                }

            }
        }
    }
}