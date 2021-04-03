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

package io.nimbly.tzatziki.markdown

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiFile

fun String.adaptPicturesPath(file: PsiFile): String {

    val references = Regex("<img +src *= *['\"]([a-z0-9-_:./]+)['\"]", RegexOption.IGNORE_CASE)
        .findAll(this)
        .toList()
        .map { it.groupValues.last() }
        .filter { !it.startsWith("http", true) }

    if (references.isEmpty())
        return this

    val root = ProjectFileIndex.SERVICE.getInstance(file.project).getSourceRootForFile(file.virtualFile)
        ?: return this

    var s = this
    references.forEach { ref ->
        val f = root.findFileByRelativePath(ref)
        if (f != null && f.exists()) {
            s = s.replace(ref, "file://${f.path}")
        }
    }
    return s
}