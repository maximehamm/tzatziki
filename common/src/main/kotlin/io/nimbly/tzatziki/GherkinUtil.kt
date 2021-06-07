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

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

fun findCucumberStepDefinitions(scenario: GherkinStepsHolder): List<AbstractStepDefinition> {
   return scenario.steps.flatMap { step ->
       step.findCucumberStepReferences().flatMap { it.resolveToDefinitions() }
   }
}

fun PsiElement.findCucumberStepReference(): CucumberStepReference?
    = findCucumberStepReferences().firstOrNull()

fun PsiElement.findCucumberStepReferences(): List<CucumberStepReference>
    = references.filterIsInstance<CucumberStepReference>()