/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.references

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

fun getCucumberStepDefinition(element: PsiElement): AbstractStepDefinition? {
    element.references.forEach { ref ->
        val def = when (ref) {
            is CucumberStepReference -> ref.resolveToDefinition()
            is TzCucumberStepReference -> ref.resolveToDefinition()
            else -> null
        }

        if (def!=null)
            return def
    }
    return null
}
