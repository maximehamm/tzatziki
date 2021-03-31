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
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement

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
            .forEach { step ->
                val location = step.getLocation(project, GlobalSearchScope.allScope(project))
                if (location != null) {
                    val element = location.psiElement.parent
                    if (element is GherkinPsiElement) {
                        steps.add(TzTestStep(step, element))
                    }
                }
            }

        TzTestRegistry.steps = steps
    }
}
