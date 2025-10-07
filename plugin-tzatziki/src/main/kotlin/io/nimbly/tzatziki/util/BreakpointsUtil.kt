package io.nimbly.tzatziki.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import io.nimbly.tzatziki.breakpoints.CUCUMBER_FAKE_EXPRESSION
import io.nimbly.tzatziki.util.JavaUtil.invokeMethod
import org.jetbrains.concurrency.Promise
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
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

fun GherkinPsiElement.toggleGherkinBreakpoint(documentLine: Int) {
    toggleAndReturnLineBreakpoint(
        project,
        containingFile.virtualFile,
        documentLine, false)
        ?.then { it: XLineBreakpoint<out XBreakpointProperties<*>>? ->
            it?.conditionExpression = null
        }
}

fun toggleAndReturnLineBreakpoint(
    project: Project,
    file: VirtualFile,
    line: Int,
    temporary: Boolean
): Promise<XLineBreakpoint<*>>? {

    // Hide to Jetbrain the use of this internal method !
    // Not a nice solution... but copying hundreds of lines of code from XDebuggerUtil is worth !
    return XDebuggerUtil.getInstance().invokeMethod("toggleAndReturnLineBreakpoint",
        listOf(Project::class.java, VirtualFile::class.java, Int::class.java, Boolean::class.java),
        mutableListOf(project, file, line, temporary)) as Promise<XLineBreakpoint<*>>?
//    return (XDebuggerUtil.getInstance() as? XDebuggerUtilImpl)?.toggleAndReturnLineBreakpoint(
//            project,
//            file,
//            line,
//            temporary)
}

fun GherkinPsiElement.deleteBreakpoints() {
    val oldBreakpoints = XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
        .filter { it.sourcePosition?.file == containingFile.virtualFile }
        .filter { it.sourcePosition?.line == getDocumentLine() }
    oldBreakpoints.forEach { b ->
        XDebuggerUtil.getInstance().removeBreakpoint(project, b)
    }
}

fun toggleCodeBreakpoint(
    codeElement: Pair<PsiElement, Int>,
    project: Project
) {
    toggleAndReturnLineBreakpoint(
        project,
        codeElement.first.containingFile.virtualFile,
        codeElement.second, false
    )
        ?.then { it: XLineBreakpoint<out XBreakpointProperties<*>>? ->
            it?.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
            (it?.properties as? JavaLineBreakpointProperties)?.let {
                // TIPS : Fix for a Kotlin exception is some cases....
                // Using reflexion because this does no more exists in Intellij newer version
                // it.lambdaOrdinal =  -1
                JavaUtil.updateField(it, "myLambdaOrdinal", -1)
            }
        }
}