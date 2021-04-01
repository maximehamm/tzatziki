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
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement

object TzTestRegistry {

    private var results: TzTestResult? = null

    fun refresh(results: TzTestResult) {
        this.results = results
    }

    fun getResults()
        = results
}

class TzTestResult {

    internal val tests = mutableMapOf<GherkinPsiElement, MutableList<SMTestProxy>>()

    fun putAll(results: TzTestResult) {
        results.tests.forEach { (elt, tests) ->
            tests.forEach { t ->
                this[elt] = t
            }
        }
    }

    operator fun set(element: GherkinPsiElement, value: SMTestProxy) {
        var l = tests[element]
        if (l == null) {
            l = mutableListOf()
            tests[element] = l
        }
        l.add(value)
    }

    operator fun get(element: GherkinPsiElement): List<SMTestProxy> {
        return tests[element] ?: emptyList()
    }

    fun remove(element: GherkinPsiElement) {
        tests.remove(element)
    }
}

val EXAMPLE_REGEX = " #[0-9]+$".toRegex()
