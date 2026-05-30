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
package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.InlineBreakpointsDisabler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.nimbly.tzatziki.util.isCucumberSyncBreakpoint

/**
 * Suppresses IntelliJ's inline-breakpoint picker (the row of mini-icons rendered
 * inside the editor next to a statement) for any file that already contains a
 * Cucumber+ code breakpoint ([TzCucumberCodeBreakpointType]).
 *
 * Why: Cucumber+ owns its breakpoints and pins them at the method body
 * (NO_LAMBDA). The inline picker would let the user spawn additional breakpoints
 * (one per lambda / sub-expression) which we cannot reliably sync with the
 * Gherkin side, hence the visual confusion the user reported (multiple icons
 * leading to "multiple breakpoint" creation).
 *
 * Per-file scope keeps the inline picker enabled in regular Java/Kotlin files
 * unrelated to Cucumber+.
 */
class TzInlineBreakpointsDisabler : InlineBreakpointsDisabler {

    override fun areInlineBreakpointsDisabled(file: VirtualFile?): Boolean {
        if (file == null) return false
        // Iterate open projects so the disabler keeps working in multi-project IDEs.
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val manager = XDebuggerManager.getInstance(project).breakpointManager
            val hasCucumberBp = manager.allBreakpoints.any { bp ->
                bp is XLineBreakpoint<*>
                    && bp.isCucumberSyncBreakpoint()  // covers JVM + JS subtypes
                    && bp.fileUrl == file.url
            }
            if (hasCucumberBp) return true
        }
        return false
    }
}
