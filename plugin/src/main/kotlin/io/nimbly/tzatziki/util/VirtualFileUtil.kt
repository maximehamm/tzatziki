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

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

fun VirtualFile.findFiles(vararg types: String, scope: GlobalSearchScope): List<VirtualFile> {
    val files = mutableSetOf<VirtualFile>()
    types.forEach {
        files.addAll(
            FileBasedIndex.getInstance().getContainingFiles(
            FileTypeIndex.NAME,
            FileTypeManager.getInstance().getFileTypeByExtension(it),
            scope))
    }
    return files.filter { it.path.startsWith(this.path) }
}