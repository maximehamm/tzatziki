package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import io.nimbly.tzatziki.util.findStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import java.util.*

class TzBreakpointType() :
    XLineBreakpointType<TzXBreakpointProperties>("tzatziki.breakpoint.type", "Cucumber+ Breakpoint") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        val step = findStep(vfile, project, line)
            ?: return false

        val reference = step.references
            .filterIsInstance<CucumberStepReference>()
            .firstOrNull()
            ?: return false

        try {
            reference.resolveToDefinition()
                ?: return false
        } catch (e: IndexNotReadyException) {
            return false
        }

        return true
    }

    override fun createBreakpointProperties(vfile: VirtualFile, line: Int): TzXBreakpointProperties? {
        return null
    }

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<TzXBreakpointProperties>,
        project: Project
    ): XDebuggerEditorsProvider? {
        // Hide conditions in breakpoint panel
        return null
    }

    override fun isSuspendThreadSupported(): Boolean {
        return false
    }

    override fun getVisibleStandardPanels(): EnumSet<StandardPanels> {
        // Hide all stuff in breakpoint panel
        val of = EnumSet.of(StandardPanels.SUSPEND_POLICY)
        of.clear()
        return of
    }
}

class TzXBreakpointProperties : XBreakpointProperties<Any>() {
    override fun getState(): Any? {
        return null
    }
    override fun loadState(state: Any) {
    }
}