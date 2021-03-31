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

package io.nimbly.tzatziki.testdiscovery

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestStatusListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import io.nimbly.tzatziki.psi.cell
import io.nimbly.tzatziki.psi.findColumnByName
import io.nimbly.tzatziki.psi.table
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

class TzTestStatusListener : TestStatusListener() {

    override fun testSuiteFinished(root: AbstractTestProxy?) {
    }

    override fun testSuiteFinished(root: AbstractTestProxy?, project: Project?) {

        if (project==null || root==null || root.children.isEmpty() || root.children.first().name != "Cucumber")
            return

        val steps = mutableListOf<TzTestStep>()
        root.allTests
            .filter { it.children.isEmpty() }
            .filterIsInstance<SMTestProxy>()
            .filter { it.locationUrl != null }
            .forEach { test ->
                val testSteps = findTestSteps(test, project)
                steps.addAll(testSteps)
            }

        TzTestRegistry.steps = steps
    }

    private fun findTestSteps(test: SMTestProxy, project: Project): List<TzTestStep> {

        // Simple step
        if (!test.parent.name.startsWith("Example #")) {

            val location = test.getLocation(project, GlobalSearchScope.allScope(project))
            val element = location?.psiElement?.parent
            if (element is GherkinPsiElement)
                return listOf(TzTestStep(test, element))
            else
                return emptyList()
        }

        // Step from example
        val rowLocation = test.parent.getLocation(project, GlobalSearchScope.allScope(project))
        val row = rowLocation?.psiElement?.parent
        if (row !is GherkinTableRow)
            return emptyList()

        val step = test.getLocation(project, GlobalSearchScope.allScope(project))?.psiElement?.parent
        if (step !is GherkinStep)
            return emptyList()

        val steps = mutableListOf<TzTestStep>()
        step.paramsSubstitutions
            .mapNotNull { row.table.findColumnByName(it) }
            .map { row.cell(it) }
            .forEach { cell ->
                steps.add(TzTestStep(test, cell))
            }

        return steps
    }
}
