/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaBreakpointHandler
import com.intellij.debugger.engine.JavaBreakpointHandlerFactory

/**
 * Registers a [JavaBreakpointHandler] for [TzCucumberCodeBreakpointType].
 *
 * Why this is required:
 *  - IntelliJ's XDebugger dispatches breakpoint handlers by EXACT class match against
 *    [XBreakpointType.javaClass] — subclasses are NOT auto-recognised.
 *  - `JavaDebugProcess` only registers the default `JavaLineBreakpointHandler` keyed
 *    on `JavaLineBreakpointType.class`, so a freshly-created `tzatziki.cucumber.code`
 *    line breakpoint (which subclasses `JavaLineBreakpointType`) shows up in the
 *    breakpoints panel but **no JDI request is ever installed for it** — the debugger
 *    runs straight through.
 *  - This factory plugs into `com.intellij.debugger.javaBreakpointHandlerFactory` so
 *    the Java debug process picks up a handler specifically for our subclass and
 *    installs the JDI breakpoint request just like it does for plain Java line BPs.
 */
class TzCucumberCodeBreakpointHandlerFactory : JavaBreakpointHandlerFactory {
    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(
        "io.nimbly.tzatziki.breakpoints.TzCucumberCodeBreakpointHandlerFactory")
    override fun createHandler(process: DebugProcessImpl): JavaBreakpointHandler {
        log.info("C+ createHandler called for process=$process")
        return TzCucumberCodeBreakpointHandler(process)
    }
}

private class TzCucumberCodeBreakpointHandler(process: DebugProcessImpl)
    : JavaBreakpointHandler(TzCucumberCodeBreakpointType::class.java, process) {

    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(
        "io.nimbly.tzatziki.breakpoints.TzCucumberCodeBreakpointHandler")

    override fun registerBreakpoint(breakpoint: com.intellij.xdebugger.breakpoints.XBreakpoint<*>) {
        log.info("C+ registerBreakpoint: ${breakpoint.type?.id} at ${breakpoint.sourcePosition?.file?.path}:${breakpoint.sourcePosition?.line}")
        super.registerBreakpoint(breakpoint)
    }
}
