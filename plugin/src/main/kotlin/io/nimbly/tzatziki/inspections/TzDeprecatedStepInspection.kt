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

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import io.nimbly.tzatziki.MAIN
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.psi.getCucumberStepDefinition
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzDeprecatedStepInspection : GherkinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

        return object : GherkinElementVisitor() {

            override fun visitStep(step: GherkinStep) {

                super.visitStep(step)

                if (!TOGGLE_CUCUMBER_PL)
                    return

                val definition = getCucumberStepDefinition(step)
                    ?: return

                val element = definition.element
                    ?: return

                val deprecated = MAIN().extensionList.find {
                    it.isDeprecated(element)
                }

                if (deprecated !=null) {

                    var start = step.text.indexOfFirst { it == ' ' } +1
                    start += step.text.substring(start).indexOfFirst { it != ' ' }
                    val eol = step.text.indexOfFirst { it == '\n' }
                    val range = TextRange(
                        start,
                        if (eol>0) eol else step.textLength)

                    holder.registerProblem(
                        step,
                        "Deprecated step",
                        ProblemHighlightType.LIKE_DEPRECATED,
                        range)
                }
            }
        }
    }
}