package io.nimbly.tzatziki.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.nimbly.tzatziki.breakpoints.TzCucumberCodeBreakpointType
import io.nimbly.tzatziki.util.JavaUtil.invokeMethod
import org.jetbrains.concurrency.Promise
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Run [block] on the EDT in a write action, immediately if we're already on the EDT,
 * otherwise via {@link ApplicationManager#invokeLater}. WriteAction itself requires EDT,
 * so callers from inspection / pool threads cannot just wrap inline.
 */
private inline fun onEdtWrite(crossinline block: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
        WriteAction.run<Throwable> { block() }
    } else {
        app.invokeLater({ WriteAction.run<Throwable> { block() } }, ModalityState.nonModal())
    }
}

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
    return XDebuggerUtil.getInstance().invokeMethod(XDEBUGGER_TOGGLE_METHOD,
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

/**
 * Returns `true` if [this] was created by Cucumber+'s Gherkin → code sync.
 * Single source of truth: replaces the legacy "fake condition" detection
 * ({@code conditionExpression == "Cucumber+"!=null}).
 */
fun XBreakpoint<*>.isCucumberSyncBreakpoint(): Boolean =
    type is TzCucumberCodeBreakpointType

/**
 * Idempotent: ensures a Cucumber+ code-side breakpoint exists at [codeElement]
 * (file + line). If one is already there, no-op. Otherwise creates a new
 * [TzCucumberCodeBreakpointType] breakpoint and pins it to the method body
 * (NO_LAMBDA) so the JVM doesn't target an inner lambda by accident.
 *
 * Async-safe: schedules the WriteAction on the EDT when called from a non-EDT thread
 * (inspection runners, breakpoint listener pool thread, etc.). Returns nothing because
 * the actual creation may be deferred — callers observe it through the breakpoint listener.
 */
fun ensureCucumberCodeBreakpoint(
    codeElement: Pair<PsiElement, Int>,
    project: Project
) {
    val file = codeElement.first.containingFile?.virtualFile ?: return
    val line = codeElement.second
    val manager = XDebuggerManager.getInstance(project).breakpointManager
    val type = XDebuggerUtil.getInstance()
        .findBreakpointType(TzCucumberCodeBreakpointType::class.java) ?: return

    val existing = manager.allBreakpoints
        .filterIsInstance<XLineBreakpoint<*>>()
        .firstOrNull { bp ->
            bp.type === type
                && bp.fileUrl == file.url
                && bp.line == line
        }
    if (existing != null) return

    onEdtWrite {
        // Re-check existence on EDT in case another listener already created it between
        // the off-EDT check above and now.
        val stillMissing = manager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .none { bp ->
                bp.type === type && bp.fileUrl == file.url && bp.line == line
            }
        if (stillMissing) {
            // Use a non-null JavaLineBreakpointProperties — Breakpoint.getProperties() is @NotNull
            // and JavaBreakpointsUsageCollector NPEs in the breakpointAdded MessageBus pump if it isn't.
            val props = JavaLineBreakpointProperties().applyNoLambda()
            manager.addLineBreakpoint(type, file.url, line, props)
        }
    }
}

/**
 * Backward-compatible alias kept for callers that historically toggled Cucumber+ breakpoints.
 * The semantics are now "ensure exists" (idempotent add) — removal goes through
 * [removeCucumberCodeBreakpoints]. If you need a true toggle, prefer the new explicit pair.
 */
fun toggleCodeBreakpoint(
    codeElement: Pair<PsiElement, Int>,
    project: Project
) {
    ensureCucumberCodeBreakpoint(codeElement, project)
}

/** Legacy marker that pre-#cucumber-scope versions used to identify their code-side breakpoints. */
private const val LEGACY_CUCUMBER_CONDITION = "\"Cucumber+\"!=null"

/**
 * One-shot migration: scans existing breakpoints and converts any that are still using the
 * legacy {@code "Cucumber+"!=null} fake-condition mechanism (a regular [JavaLineBreakpointType])
 * into our [TzCucumberCodeBreakpointType]. Idempotent — once a breakpoint has been migrated,
 * subsequent calls find nothing to do.
 *
 * Called at project startup so users keep their breakpoints across the upgrade.
 */
fun migrateLegacyCucumberCodeBreakpoints(project: Project) {
    val manager = XDebuggerManager.getInstance(project).breakpointManager
    val candidates = manager.allBreakpoints
        .filterIsInstance<XLineBreakpoint<*>>()
        .filter { bp ->
            // Already our type? skip.
            bp.type !is TzCucumberCodeBreakpointType
                // Was marked by the legacy fake condition?
                && bp.conditionExpression?.expression == LEGACY_CUCUMBER_CONDITION
        }
    if (candidates.isEmpty()) return
    candidates.forEach { legacy ->
        // Clear the fake condition before promoting so it doesn't survive the migration.
        legacy.conditionExpression = null
        promoteToCucumberType(legacy, project)
    }
}

/**
 * "Promotes" a user-created Java line breakpoint that was placed on a Cucumber step
 * definition method into a [TzCucumberCodeBreakpointType] breakpoint. Preserves user
 * settings (enabled, suspend policy, condition, log expression) and applies the same
 * NO_LAMBDA fix as a freshly-created Cucumber+ breakpoint.
 *
 * The new typed breakpoint is added BEFORE the original is removed so that any listener
 * inspecting "how many code breakpoints are on this method?" still sees a count of 1
 * during the transition (avoids spurious "0 code breakpoints → cleanup Gherkin bps").
 */
fun promoteToCucumberType(userBp: XLineBreakpoint<*>, project: Project) {
    val file = userBp.sourcePosition?.file ?: return
    val line = userBp.line
    val manager = XDebuggerManager.getInstance(project).breakpointManager
    val type = XDebuggerUtil.getInstance()
        .findBreakpointType(TzCucumberCodeBreakpointType::class.java) ?: return

    // Snapshot user's settings off EDT — they don't change in the meantime.
    val wasEnabled = userBp.isEnabled
    val suspendPolicy = userBp.suspendPolicy
    val condition = userBp.conditionExpression
    val logExpr = userBp.logExpressionObject

    onEdtWrite {
        val props = JavaLineBreakpointProperties().applyNoLambda()
        val ourBp = manager.addLineBreakpoint(type, file.url, line, props)
        ourBp.isEnabled = wasEnabled
        ourBp.suspendPolicy = suspendPolicy
        ourBp.conditionExpression = condition
        ourBp.logExpressionObject = logExpr
        manager.removeBreakpoint(userBp)
    }
}

/**
 * Pin the breakpoint to the method body (NOT a lambda on the same line).
 * 2025.3+ replaces {@code myLambdaOrdinal} with {@code setEncodedInlinePosition}/NO_LAMBDA.
 */
private fun JavaLineBreakpointProperties.applyNoLambda(): JavaLineBreakpointProperties {
    val newApiOk = runCatching {
        val noLambda = JavaLineBreakpointProperties::class.java.getField("NO_LAMBDA").getInt(null)
        JavaLineBreakpointProperties::class.java
            .getMethod("setEncodedInlinePosition", Integer::class.java)
            .invoke(this, noLambda)
    }.isSuccess
    if (!newApiOk) {
        JavaUtil.updateField(this, JAVA_LINE_BP_LAMBDA_ORDINAL_FIELD, -1)
    }
    return this
}

const val XDEBUGGER_TOGGLE_METHOD           = "toggleAndReturnLineBreakpoint"
const val JAVA_LINE_BP_LAMBDA_ORDINAL_FIELD = "myLambdaOrdinal"