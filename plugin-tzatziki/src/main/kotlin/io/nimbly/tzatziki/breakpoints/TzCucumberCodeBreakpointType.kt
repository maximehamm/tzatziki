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

import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.debugger.ui.breakpoints.LineBreakpoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import icons.ActionIcons
import javax.swing.Icon
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties

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

    // NOTE: we deliberately DO NOT override computeVariants / computeVariantsAsync here.
    // Returning an empty list — as a previous version did — visually suppresses IntelliJ's
    // inline-position picker, but it also breaks the JVM debugger: with no variant to
    // match, JavaLineBreakpointType.createJavaBreakpoint never installs a JDI request,
    // so the breakpoint never fires. The inline picker is now suppressed per-file by
    // TzInlineBreakpointsDisabler, which leaves the variant chain intact.

    /**
     * Build the actual Java breakpoint instance. We return a Cucumber+ subclass so the
     * "verified" state (gutter icon when the JVM has installed the JDI request) gets the
     * Cucumber+ green badge instead of the plain JetBrains red-dot-with-tick — which
     * would otherwise lose our identity once the debugger arms the breakpoint.
     */
    override fun createJavaBreakpoint(
        project: Project,
        breakpoint: XBreakpoint<JavaLineBreakpointProperties>
    ): Breakpoint<JavaLineBreakpointProperties> = TzCucumberCodeBreakpoint(project, breakpoint)
}

/**
 * Java-side Cucumber+ breakpoint — same JDI behaviour as the platform's [LineBreakpoint]
 * (which is what JavaLineBreakpointType creates by default), with a custom gutter icon
 * for the "verified" state so the Cucumber+ green badge survives once the JVM has armed
 * the breakpoint.
 */
private class TzCucumberCodeBreakpoint(
    project: Project,
    breakpoint: XBreakpoint<JavaLineBreakpointProperties>
) : LineBreakpoint<JavaLineBreakpointProperties>(project, breakpoint) {

    override fun getVerifiedIcon(isMuted: Boolean): Icon =
        if (xBreakpoint.suspendPolicy == SuspendPolicy.NONE)
            ActionIcons.BREAKPOINT_CUCUMBER_CODE_VERIFIED_NO_SUSPEND
        else
            ActionIcons.BREAKPOINT_CUCUMBER_CODE_VERIFIED
}
