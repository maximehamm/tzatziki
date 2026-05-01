/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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
package io.nimbly.tzatziki.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore

/**
 * Step-indexing scope helpers — see [TzPersistenceStateService.stepScope] and issue #104.
 *
 * The auto-detection rule walks up from the .feature file's directory and stops at
 * the first marker file:
 *   1. `.cucumber-scope`           (explicit user-defined anchor — wins)
 *   2. `package.json`              (typical JS/TS workspace)
 *   3. `pom.xml`                   (Maven module)
 *   4. `build.gradle.kts` / `build.gradle` (Gradle module)
 *
 * The directory containing the marker becomes the *anchor*. Step definitions
 * outside this anchor are filtered out from completion / Cmd+Click resolution
 * / Find Usages.
 *
 * If no marker is found up to the project root, no filtering is applied.
 */
object StepScope {

    /** Marker filenames, in priority order. */
    private val SCOPE_ANCHOR_FILES = listOf(
        ".cucumber-scope",
        "package.json",
        "pom.xml",
        "build.gradle.kts",
        "build.gradle",
    )

    /**
     * Returns the scope anchor [VirtualFile] (a directory) for the given file,
     * or `null` when no anchor is found.
     *
     * Always returns `null` if the user disabled scoping (`OFF` mode).
     */
    fun anchorFor(project: Project, file: VirtualFile?): VirtualFile? {
        if (file == null) return null
        val state = project.getService(TzPersistenceStateService::class.java)
        if (state.stepScope != StepScopeMode.AUTO) return null
        return walkUpForAnchor(project, file)
    }

    fun anchorFor(element: PsiElement?): VirtualFile? {
        val vf = element?.containingFile?.virtualFile ?: return null
        return anchorFor(element.project, vf)
    }

    fun anchorFor(file: PsiFile?): VirtualFile? {
        val vf = file?.virtualFile ?: return null
        return anchorFor(file.project, vf)
    }

    /**
     * Returns true when AUTO mode is enabled and at least one scope anchor exists for [file].
     * When false, callers should skip any scope-based filtering.
     */
    fun isScopeActive(project: Project, file: VirtualFile?): Boolean =
        anchorFor(project, file) != null

    /**
     * Builds a [GlobalSearchScope] restricted to the anchor directory of [file].
     * Returns `null` when no scope applies — callers should keep their default scope.
     */
    fun searchScopeFor(project: Project, file: VirtualFile?): GlobalSearchScope? {
        val anchor = anchorFor(project, file) ?: return null
        return GlobalSearchScopesCore.directoryScope(project, anchor, /* withSubdirectories = */ true)
    }

    /**
     * Returns true if [candidate] (a step definition's file/element) belongs to the same
     * scope anchor as [origin] (typically the .feature file). When no anchor is found for
     * [origin], the predicate is permissive (returns true) — never filter when we don't know.
     */
    fun isInSameScope(origin: VirtualFile?, candidate: VirtualFile?, project: Project): Boolean {
        if (origin == null || candidate == null) return true
        val anchor = anchorFor(project, origin) ?: return true
        // candidate is in scope if it's under the anchor directory
        return com.intellij.openapi.vfs.VfsUtilCore.isAncestor(anchor, candidate, /* strict = */ false)
    }

    private fun walkUpForAnchor(project: Project, start: VirtualFile): VirtualFile? {
        val projectRoot = project.guessProjectDir()
        var dir: VirtualFile? = if (start.isDirectory) start else start.parent
        while (dir != null) {
            for (markerName in SCOPE_ANCHOR_FILES) {
                val marker = dir.findChild(markerName)
                if (marker != null && !marker.isDirectory) {
                    return dir
                }
            }
            // Stop at project root inclusive (we still check it for markers).
            if (projectRoot != null && dir == projectRoot) break
            dir = dir.parent
        }
        return null
    }
}
