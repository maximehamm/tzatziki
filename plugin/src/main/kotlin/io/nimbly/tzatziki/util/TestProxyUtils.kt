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

package io.nimbly.tzatziki.util

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import io.nimbly.tzatziki.editor.TEST_IGNORED
import io.nimbly.tzatziki.editor.TEST_KO
import io.nimbly.tzatziki.editor.TEST_OK
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

val SMTestProxy.textAttribut: TextAttributesKey
    get() {
        return when {
            isIgnored -> TEST_IGNORED
            isDefect -> TEST_KO
            else -> TEST_OK
        }
    }

val SMTestProxy.index get()
    = parent.children.indexOf(this)

fun SMTestProxy.isExample(project: Project): Boolean {
    val p1 = findElement(project)
    return (p1 is LeafPsiElement
            && p1.parent is GherkinTableRow)
}

fun SMTestProxy.findElement(project: Project): PsiElement? {
    var location = getLocation(project, GlobalSearchScope.allScope(project))
    if (location == null) {

        // Workaround for path location issue
        // Example: file://file:///Users/Maxime/Development/projects-temp/temp3/src/test/resources/features//Users/Maxime/Development/projects-temp/temp3/src/test/resources/features/add.feature:12
        val url = locationUrl
        if (url?.startsWith("file://file://") == true) {
            val fake = SMTestProxy(name, isSuite, "file://" + url.substring(14), metainfo, isPreservePresentableName)
            fake.locator = locator
            location = fake.getLocation(project, GlobalSearchScope.allScope(project))
        }
    }

    return location?.psiElement
}