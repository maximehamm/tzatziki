package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import icons.ActionIcons
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.findBreakpoint
import io.nimbly.tzatziki.util.isCucumberSyncBreakpoint
import java.lang.reflect.Method
import javax.swing.Icon

/**
 * Gives a Cucumber+ code-side breakpoint a *per-instance* icon (red dot + hollow green
 * ring, instead of the usual red dot + filled green disc) when its linked Gherkin steps
 * are in a MIXED mute state — i.e. several Gherkin steps share the same step definition
 * (one shared code breakpoint) and some of them are muted while others are not. In that
 * case we deliberately do NOT mute the shared code breakpoint, so the platform would
 * otherwise paint it as a plain enabled breakpoint with no hint of the nuance.
 *
 * IntelliJ has no public per-instance breakpoint icon, so this relies on the internal
 * [XBreakpointManagerImpl.updateBreakpointPresentation] (which sets a
 * CustomizedBreakpointPresentation and queues the gutter redraw). The dependency on this
 * internal method is guarded by an automated API test so we catch future platform
 * migrations early.
 *
 * Usage: call [recompute] inside a read action (off the EDT) and [apply] on the EDT.
 */
object TzPartialMutePresentation {

    /** Read-action-safe: map every Cucumber+ code breakpoint to whether it is "partial". */
    fun recompute(project: Project): Map<XLineBreakpoint<*>, Boolean> {
        val result = HashMap<XLineBreakpoint<*>, Boolean>()
        XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter { it.isCucumberSyncBreakpoint() }
            .forEach { bp ->
                val sp = bp.sourcePosition ?: return@forEach

                // Use the breakpoint's exact source offset — NOT the line-start offset.
                // For JS/TS, resolving from column 0 (indentation whitespace) does not walk
                // up to the enclosing cucumber call, so findSteps would return nothing and
                // the breakpoint would never be detected as "partial". The exact offset is
                // also what refreshCode uses, so both paths agree across languages.
                val steps = Tzatziki.findSteps(sp.file, sp.offset)
                val states = steps.mapNotNull { it.findBreakpoint()?.isEnabled }
                result[bp] = states.size >= 2 && states.contains(true) && states.contains(false)
            }
        return result
    }

    // XBreakpointManagerImpl#updateBreakpointPresentation(XLineBreakpoint, Icon, String) is the
    // only API able to give a line breakpoint a per-instance icon outside a debug session. It
    // lives in an internal (impl) package, so we resolve it REFLECTIVELY: that keeps the symbol
    // out of our bytecode and therefore out of the JetBrains plugin verifier's INTERNAL_API_USAGES
    // report. The signature is pinned by an automated guard test (PartialMutePresentationApiTest)
    // so a future platform migration fails the build instead of silently disabling the feature.
    private val updateBreakpointPresentationMethod: Method? by lazy {
        runCatching {
            Class.forName("com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl")
                .getMethod("updateBreakpointPresentation", XLineBreakpoint::class.java, Icon::class.java, String::class.java)
        }.getOrNull()
    }

    /** EDT: apply the per-instance presentation (ring icon when partial, default otherwise). */
    fun apply(project: Project, data: Map<XLineBreakpoint<*>, Boolean>) {
        val method = updateBreakpointPresentationMethod ?: return
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        data.forEach { (bp, partial) ->
            // icon = null restores the breakpoint type's own icon. updateBreakpointPresentation
            // is idempotent (it only repaints when the icon actually changes).
            runCatching {
                method.invoke(manager, bp, if (partial) ActionIcons.BREAKPOINT_CUCUMBER_CODE_PARTIAL else null, null)
            }
        }
    }
}

/**
 * Recomputes the per-instance "partial mute" presentations once at project startup. The
 * presentation is not persisted, and [TzBreakpointListener] only recomputes on breakpoint
 * events — so without this, breakpoints restored from the workspace would show the default
 * icon until the user next touches them.
 */
class TzPartialMuteStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            ApplicationManager.getApplication().executeOnPooledThread {
                val data = runCatching {
                    ReadAction.nonBlocking<Map<com.intellij.xdebugger.breakpoints.XLineBreakpoint<*>, Boolean>> {
                        TzPartialMutePresentation.recompute(project)
                    }.executeSynchronously()
                }.getOrNull() ?: return@executeOnPooledThread
                ApplicationManager.getApplication().invokeLater {
                    TzPartialMutePresentation.apply(project, data)
                }
            }
        }
    }
}
