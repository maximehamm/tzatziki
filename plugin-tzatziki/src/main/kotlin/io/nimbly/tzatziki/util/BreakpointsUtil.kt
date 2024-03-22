package io.nimbly.tzatziki.util

import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep

fun GherkinStep.updatePresentation(breakpoints: List<XBreakpoint<*>>) {

    val enabled = breakpoints.map { if (it.isEnabled) 1 else 0 }.sum()
    val condition = breakpoints.map { it.conditionExpression }.filterNotNull().firstOrNull()

    val oldBreakpoints = XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
        .filter { it.sourcePosition?.file == containingFile.virtualFile }
        .filter { it.sourcePosition?.line == getDocumentLine() }
    oldBreakpoints.forEach { b ->
        b.isEnabled = enabled > 0
        b.conditionExpression = condition
    }
}