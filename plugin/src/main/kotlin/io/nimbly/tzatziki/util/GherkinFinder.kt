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

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTag

private val CacheTagsKey: Key<CachedValue<List<Tag>>> = Key.create("io.nimbly.tzatziki.util.tagsfinder")

/**
 * find all gherkin files
 */
fun findAllGerkinsFiles(project: Project): Set<GherkinFile> {

    val scope = project.getGherkinScope()

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

/**
 * find all gherkin tags
 */
fun findAllTags(project: Project, scope: GlobalSearchScope): Set<Tag> {
    val allTags = mutableSetOf<Tag>()
    FilenameIndex
        .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, scope)
        .map { vfile -> vfile.getFile(project) }
        .filterIsInstance<GherkinFile>()
        .forEach { file ->
            val tags = CachedValuesManager.getCachedValue(file, CacheTagsKey) {

                val tags = PsiTreeUtil.collectElements(file) { element -> element is GherkinTag }
                    .map { Tag(it as GherkinTag) }
                    .filter { it.name.isNotEmpty() }

                CachedValueProvider.Result.create(
                    tags,
                    PsiModificationTracker.MODIFICATION_COUNT, file
                )
            }
            allTags.addAll(tags)
        }
    return allTags
}

data class Tag(val tag: GherkinTag) {
    val name: String by lazy { tag.name.substringAfter("@") }
    val filename: String by lazy { tag.containingFile.name }
}