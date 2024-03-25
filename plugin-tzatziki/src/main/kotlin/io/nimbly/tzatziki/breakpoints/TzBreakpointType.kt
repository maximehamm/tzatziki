package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import io.nimbly.tzatziki.util.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import java.util.*

class TzBreakpointType() :
    XLineBreakpointType<TzXBreakpointProperties>("tzatziki.breakpoint.type", "Cucumber+ Breakpoint") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        val file = vfile.getFile(project) ?: return false
        val doc = file.getDocument() ?: return false
        val lineRange = doc.getLineRange(line).shrink(1, 1)
        val step = file.findElementsOfTypeInRange(lineRange, GherkinStep::class.java).firstOrNull()
        if (step != null) {

            if (line == step.getDocumentLine())
                return true
            // try {
            //     val reference = step.references
            //         .filterIsInstance<CucumberStepReference>()
            //         .firstOrNull()
            //         ?: return false
            //     val stepDefinition = reference.resolveToDefinition()
            //     if (stepDefinition != null)
            //         return true
            // } catch (ignored: IndexNotReadyException) {
            // }
             return false
        }

        val row = file.findElementsOfTypeInRange(lineRange, GherkinTableRow::class.java).firstOrNull()
        if (row != null && row !is GherkinTableHeaderRowImpl) {
            val examples = row.getParentOfTypes(true, GherkinExamplesBlock::class.java)
            return examples != null
        }

        return false
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