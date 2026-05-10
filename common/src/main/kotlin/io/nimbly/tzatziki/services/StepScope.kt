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
 * Step-indexing scope helpers — issue #104.
 *
 * Activation rule (no toggle): we ONLY filter when an explicit `.cucumber-scope` marker
 * file is found by walking up from the .feature file. Without that file, no filtering
 * takes place — and if the project is properly configured into multiple IntelliJ modules,
 * native module isolation already does the right thing.
 *
 * The directory containing `.cucumber-scope` becomes the *anchor*. Step definitions
 * outside this anchor are filtered out from completion / Cmd+Click resolution / Find Usages.
 */
object StepScope {

    /** The single explicit scope marker filename. */
    private const val SCOPE_ANCHOR_FILE = ".cucumber-scope"

    /**
     * Returns the scope anchor [VirtualFile] (a directory) for the given file,
     * or `null` when no `.cucumber-scope` marker is found in any ancestor directory.
     */
    fun anchorFor(project: Project, file: VirtualFile?): VirtualFile? {
        if (file == null) return null
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
            val marker = dir.findChild(SCOPE_ANCHOR_FILE)
            if (marker != null && !marker.isDirectory) {
                return dir
            }
            // Stop at project root inclusive (we still check it for the marker).
            if (projectRoot != null && dir == projectRoot) break
            dir = dir.parent
        }
        return null
    }
}
