/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.breakpoints

import com.intellij.javascript.debugger.breakpoints.JavaScriptLineBreakpointProperties
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import icons.ActionIcons
import javax.swing.Icon

/**
 * JavaScript / TypeScript counterpart of [io.nimbly.tzatziki.breakpoints.TzCucumberCodeBreakpointType]:
 * a line-breakpoint type that the Cucumber+ Gherkin↔code sync uses to mark step-def
 * body lines on JS / TS files with the green Cucumber+ disc (instead of the platform's
 * native red dot).
 *
 * Created **programmatically only** — [canPutAt] always returns `false`, so the user
 * can't pick it from the gutter. The standard [com.intellij.javascript.debugger.breakpoints.JavaScriptBreakpointType]
 * remains the only manually-creatable JS breakpoint type.
 *
 * ⚠️ Platform limitation: `XBreakpointType.getId()` is `final`, and `JavaScriptBreakpointType`'s
 * constructor is no-arg with a hard-coded id. We therefore cannot extend it — we extend
 * [XLineBreakpointType] directly and reuse its [JavaScriptLineBreakpointProperties] so
 * the JS debugger can still recognise our instances by the properties class. Whether
 * the JS debugger actually installs CDP/V8 breakpoints for our type remains to be
 * validated end-to-end; if it doesn't, we fall back to the native type and only the
 * gutter icon stays customisable.
 */
class TzCucumberJsBreakpointType : XLineBreakpointType<JavaScriptLineBreakpointProperties>(
    /* id    = */ "tzatziki.cucumber.code.javascript",
    /* title = */ "Cucumber+ Step (JS)",
) {

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        // Programmatic creation only — never picked when the user clicks the gutter.
        return false
    }

    override fun createBreakpointProperties(file: VirtualFile, line: Int): JavaScriptLineBreakpointProperties =
        JavaScriptLineBreakpointProperties()

    // Same Cucumber+ green badge over the standard red dot as the JVM type —
    // gives users a consistent visual across step-def languages.
    override fun getEnabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_ENABLED
    override fun getDisabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_DISABLED
    override fun getMutedEnabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_MUTED_ENABLED
    override fun getMutedDisabledIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_MUTED_DISABLED
    override fun getSuspendNoneIcon(): Icon = ActionIcons.BREAKPOINT_CUCUMBER_CODE_NO_SUSPEND
}
