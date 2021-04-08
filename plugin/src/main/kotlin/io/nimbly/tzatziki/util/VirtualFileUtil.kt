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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

fun VirtualFile.findFiles(vararg types: String, project: Project): List<VirtualFile> {

    val files = mutableSetOf<VirtualFile>()
    types.forEach {
        files.addAll(
            FilenameIndex.getAllFilesByExt(project, it, GlobalSearchScope.projectScope(project)))
    }
    return files.filter { it.path.startsWith(this.path) }
}


fun VirtualFile.chooseFileName(name: String, ext: String): String {

    fun ok(n: String): Boolean {
        val child = findChild("$n.$ext")
        if (child == null || !child.exists() || child.isWritable) {
            return true
        }
        return false
    }

    if (ok(name))
        return "$name.$ext";


    for (i in 0..10) {
        val n = name + i
        if (ok(n))
            return "$n.$ext"
    }

    return "$name.$ext"
}