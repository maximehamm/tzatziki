package io.nimbly.tzatziki.breakpoints

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl
import java.util.*

class TzStepBreakpointType: TzBreakpointType("tzatziki.gherkin.step", "Cucumber+ Step") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        if (!isJavaPresent())
            return false

        val file = vfile.getFile(project) ?: return false
        val doc = file.getDocument() ?: return false
        val lineRange = doc.getLineRange(line).shrink(1, 1)
        val step = file.findElementsOfTypeInRange(lineRange, GherkinStep::class.java).firstOrNull()
        if (step != null && line == step.getDocumentLine())
            return true

        return false
    }

    override fun getDisplayText(breakpoint: XLineBreakpoint<TzXBreakpointProperties>?)
            = "Cucumber+ Step"
}


class TzStepExampleBreakpointType() : TzBreakpointType("tzatziki.gherkin.step.example", "Cucumber+ Step example") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        try {
            val file = vfile.getFile(project) ?: return false
            val doc = file.getDocument() ?: return false
            val lineRange = doc.getLineRange(line).shrink(1, 1)

            val row = file.findElementsOfTypeInRange(lineRange, GherkinTableRow::class.java).firstOrNull()
            if (row != null && row !is GherkinTableHeaderRowImpl) {
                val examples = row.parentOfTypeIs<GherkinExamplesBlock>(true)
                return examples != null
            }

            return false

        } catch (e: NoClassDefFoundError) {
            // Happed if Jetbrain product does dot support Java at all
            return false
        }
    }

    override fun getDisplayText(breakpoint: XLineBreakpoint<TzXBreakpointProperties>?): String {

        val text = "Cucumber+ Example"

        val line = breakpoint?.sourcePosition?.line ?: return text
        val vfile = breakpoint.sourcePosition?.file ?: return text
        val project = vfile.findProject() ?: return text
        val file = vfile.getFile(project) ?: return text
        val doc = file.getDocument() ?: return text

        val lineRange = doc.getLineRange(line).shrink(1, 1)
        val row = file.findElementsOfTypeInRange(lineRange, GherkinTableRow::class.java).firstOrNull()
            ?: return text

        if (row is GherkinTableHeaderRowImpl)
            return text

        val examples = row.parentOfTypeIs<GherkinExamplesBlock>(true)
            ?: return text

        val scenario = examples.parentOfTypeIs<GherkinScenarioOutline>(true)
            ?: return text

        val index = scenario.allExamples().indexOf(row)
        if (index < 0)
            return text

        return text + " #" + (index+1)
    }

    override fun getEnabledIcon()= AllIcons.Debugger.Db_field_breakpoint
    override fun getDisabledIcon() = AllIcons.Debugger.Db_disabled_field_breakpoint
    override fun getMutedEnabledIcon() = AllIcons.Debugger.Db_muted_field_breakpoint
    override fun getMutedDisabledIcon()  = AllIcons.Debugger.Db_muted_disabled_field_breakpoint
    override fun getSuspendNoneIcon() = AllIcons.Debugger.Db_no_suspend_field_breakpoint

    override fun shouldShowInBreakpointsDialog(project: Project): Boolean {
        return false
    }
}

abstract class TzBreakpointType(id: String, title: String) : XLineBreakpointType<TzXBreakpointProperties>(id, title) {

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
        // Hide suspend field in breakpoint panel
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