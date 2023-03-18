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

package io.nimbly.tzatziki.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

/**
 * find all gherkin files
 */
fun findAllGerkinsFiles(project: Project): Set<GherkinFile> {
    val scope = project.getGherkinScope()
    return findAllGerkinsFiles(scope, project)
}

fun findAllGerkinsFiles(module: Module, recursive: Boolean = false): Set<GherkinFile> {
    val scope = module.getGherkinScope(recursive)
    return findAllGerkinsFiles(scope, module.project)
}

private fun findAllGerkinsFiles(scope: GlobalSearchScope, project: Project): Set<GherkinFile> {

    val allFeatures = mutableSetOf<GherkinFile>()
    FilenameIndex
        .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, scope)
        .map { vfile -> vfile.getFile(project) }
        .filterIsInstance<GherkinFile>()
        .forEach { file ->
            allFeatures.add(file)
        }

    return allFeatures
}
