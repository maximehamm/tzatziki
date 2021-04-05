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

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

object TzTestRegistry {

    private var activeResults = TzTestResult()

    fun refresh(results: TzTestResult) {

        // Clone previous results
        val temp = activeResults.clone()

        // Get involved scenario
        val involvedScenarios = results.tests.mapNotNull { it.value.scenario }.toSet()

        // Retain all related to not-involved scenarios
        temp.tests = temp.tests
            .filter { !involvedScenarios.contains(it.value.scenario) }
            .toMutableMap()

        // Add new results
        temp.putAll(results)

        this.activeResults = temp
    }

    fun cleanTestsResults(file: PsiFile, editor: Editor) {

        if (activeResults.tests.isEmpty())
            return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val scenario = PsiTreeUtil.getContextOfType(element, GherkinStepsHolder::class.java) ?: return

        // Retain all related to not-involved scenarios
        activeResults.tests = activeResults.tests
            .filter { scenario != it.value.scenario }
            .toMutableMap()

    }

    val results get() = activeResults
}

class TzTestResult {

    internal var tests = mutableMapOf<GherkinPsiElement, TzTestItem>()

    fun putAll(results: TzTestResult) {
        results.tests.forEach { (elt, item) ->
            item.tests.forEach { t ->
                this[elt] = t
            }
        }
    }

    operator fun set(element: GherkinPsiElement, value: SMTestProxy) {
        var l = tests[element]
        if (l == null) {
            l = TzTestItem(element)
            tests[element] = l
        }
        l.add(value)
    }

    operator fun get(element: GherkinPsiElement): Set<SMTestProxy> {
        return tests[element]?.tests ?: emptySet()
    }

    fun clone(): TzTestResult {
        val tests = this.tests
            .map { it.key to TzTestItem(it.key, it.value.scenario, it.value.tests.toMutableSet()) }
            .toMap()
        val r = TzTestResult()
        r.tests.putAll(tests)
        return r
    }

}

internal class TzTestItem(
    element: GherkinPsiElement,
    val scenario: GherkinStepsHolder? = element.parentScenario,
    val tests: MutableSet<SMTestProxy> = mutableSetOf()) {

    fun add(testProxy: SMTestProxy) {
        tests.add(testProxy)
    }
}

private val GherkinPsiElement.parentScenario
    get() = PsiTreeUtil.getNonStrictParentOfType(this, GherkinStepsHolder::class.java)