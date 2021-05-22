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

package io.nimbly.tzatziki.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateAllStepsFix
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateStepFix
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

//https://youtrack.jetbrains.com/issue/IDEA-269898
@Deprecated("Remove this when Jetbrain issue IDEA-269898 will be fixed")
class TzCucumberStepInspection : GherkinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : GherkinElementVisitor() {
            override fun visitStep(step: GherkinStep) {

                super.visitStep(step)
                if (step.parent !is GherkinStepsHolder)
                    return

                val reference = step.references
                    .filterIsInstance<CucumberStepReference>()
                    .firstOrNull()
                    ?: return

                val definition = reference.resolveToDefinition()
                if (definition != null)
                    return

                var fix: CucumberCreateStepFix? = null
                var allStepsFix: CucumberCreateAllStepsFix? = null
                if (CucumberStepHelper.getExtensionCount() > 0) {
                    fix = CucumberCreateStepFix()
                    allStepsFix = CucumberCreateAllStepsFix()
                }
                holder.registerProblem(
                    reference.element,
                    reference.rangeInElement,
                    CucumberBundle.message("cucumber.inspection.undefined.step.msg.name", *arrayOfNulls(0)),
                    fix, allStepsFix
                )
            }
        }
    }

    override fun isEnabledByDefault(): Boolean {
        return true
    }

    override fun getShortName(): String {
        return "CucumberPlusUndefinedStep"
    }
}