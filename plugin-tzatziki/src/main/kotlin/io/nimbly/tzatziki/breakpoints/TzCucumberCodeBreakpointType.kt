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

import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XSourcePosition
import icons.ActionIcons
import javax.swing.Icon
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * Code-side counterpart of [TzStepBreakpointType] for Java/Kotlin step definition methods.
 *
 * Created **only programmatically** by the Cucumber+ breakpoint sync (Gherkin → Java).
 * The user cannot create one by clicking the gutter — [canPutAt] always returns false —
 * so the standard [JavaLineBreakpointType] is the one offered for manual breakpoints
 * elsewhere in code.
 *
 * Why a custom type instead of a regular Java line breakpoint with a fake
 * `"Cucumber+"!=null` condition?
 *  - Cleaner semantic: the plugin recognises its breakpoints by **type**, not by parsing a
 *    fake condition string the user could see and break.
 *  - Distinct visual (Cucumber+ green dot) so users know which side of the sync owns the bp.
 *  - Listed under "Cucumber+ Step (code)" in the Breakpoints panel.
 *
 * Behaviourally identical to a Java line breakpoint — the JVM debugger sees us as a
 * subclass of [JavaLineBreakpointType] and creates the same JDI requests.
 */
class TzCucumberCodeBreakpointType : JavaLineBreakpointType(
    /* id = */ "tzatziki.cucumber.code",
    /* title = */ "Cucumber+ Step code"
) {

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        // Programmatic creation only — never picked when the user clicks the gutter.
        return false
    }

    override fun getDisplayName(): String = "Cucumber+ Step code"

    // Java/Kotlin side: keep the standard JetBrains breakpoint glyph (so the language is
    // immediately recognizable) and add a small Cucumber+ green badge in the corner.
    override fun getEnabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_ENABLED
    override fun getDisabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_DISABLED
    override fun getMutedEnabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_MUTED_ENABLED
    override fun getMutedDisabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_MUTED_DISABLED
    override fun getSuspendNoneIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_NO_SUSPEND

    /**
     * Suppress IntelliJ's inline-position picker for Cucumber+ code breakpoints.
     *
     * The base [JavaLineBreakpointType.computeVariants] inspects the body line and may
     * return one variant per "stoppable" expression (the whole line, each method call,
     * each lambda…). When more than one variant exists, the editor draws inline icons
     * (the small dots in the editor next to the statement) and lets the user pick one,
     * which can result in *multiple* breakpoints living on the same line.
     *
     * For Cucumber+ breakpoints we always want a single line-level breakpoint pinned to
     * the method body (NO_LAMBDA). Returning an empty list disables the inline picker
     * entirely.
     */
    override fun computeVariants(
        project: Project,
        position: XSourcePosition
    ): List<JavaBreakpointVariant> = emptyList()

    /**
     * Modern async path used by [com.intellij.xdebugger.impl.breakpoints.InlineBreakpointInlayManager].
     * The default implementation in [com.intellij.xdebugger.breakpoints.XLineBreakpointType] delegates
     * to the sync [computeVariants] but on a background thread; depending on the IDE version it may be
     * the only one queried, so we override both to be safe.
     */
    override fun computeVariantsAsync(
        project: Project,
        position: XSourcePosition
    ): Promise<List<XLineBreakpointVariant>> = resolvedPromise(emptyList())
}
