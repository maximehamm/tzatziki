package io.nimbly.tzatziki.util

import com.intellij.psi.PsiElement
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep

fun GherkinStep.updatePresentation(codeBreakpoints: List<XBreakpoint<*>>) {

    val enabled = codeBreakpoints.map { if (it.isEnabled) 1 else 0 }.sum()
    val condition = codeBreakpoints.map { it.conditionExpression }.filterNotNull().firstOrNull()

    val stepBreakpoints = XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
        .filter { it.sourcePosition?.file == containingFile.virtualFile }
        .filter { it.sourcePosition?.line == getDocumentLine() }
    stepBreakpoints.forEach { b ->
        b.isEnabled = enabled > 0
    }
}

fun PsiElement.findBreakpoint(): XBreakpoint<*>? {
    return XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
        .filter { it.sourcePosition?.file == containingFile.virtualFile }
        .firstOrNull { it.sourcePosition?.line == getDocumentLine() }
}