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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

fun String.adaptPicturesPath(file: PsiFile): String {

    val root = ProjectFileIndex.SERVICE.getInstance(file.project).getSourceRootForFile(file.virtualFile)
        ?: return this

    var s = this
    Regex("<img +src *= *['\"]([a-z0-9-_:./]+)['\"]", RegexOption.IGNORE_CASE)
        .findAll(this)
        .toList()
        .reversed()
        .forEach {
            val group = it.groups.last()!!
            val r = TextRange(group.range.first, group.range.last+1)
            val path = r.substring(s)
            val f = root.findFileByRelativePath(path)
            if (f != null && f.exists()) {
                s = r.replace(s, "file://${f.path}")
            }
         }
    return s
}

fun String.getRelativePath(file: PsiFile): String? {

    if (this.startsWith("http", true))
        return this

    val root = ProjectFileIndex.SERVICE.getInstance(file.project).getSourceRootForFile(file.virtualFile)
        ?: return null

    val f = root.findFileByRelativePath(this)
    if (f != null && f.exists()) {
        return f.path
    }

    return null
}