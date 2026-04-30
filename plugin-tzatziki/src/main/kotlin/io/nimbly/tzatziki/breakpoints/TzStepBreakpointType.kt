package io.nimbly.tzatziki.breakpoints

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
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

        if (vfile.extension != "feature") return false
        if (!isJavaPresent()) return false

        // Text-based check is the primary guard — immune to transient PSI re-parse.
        // In 2025.3+ the backend calls canPutAt again ~500ms after creation on a
        // background thread; PSI may be unavailable at that point.
        val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return false
        return isLikelyStepLine(doc, line)
    }

    override fun getDisplayText(breakpoint: XLineBreakpoint<TzXBreakpointProperties>?)
            = "Cucumber+ Step"
}


class TzStepExampleBreakpointType() : TzBreakpointType("tzatziki.gherkin.step.example", "Cucumber+ Step example") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        try {
            if (vfile.extension != "feature") return false

            val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return false
            return isLikelyExampleRowLine(doc, line)

        } catch (e: NoClassDefFoundError) {
            // Happens if JetBrains product does not support Java at all
            return false
        }
    }

    override fun getDisplayText(breakpoint: XLineBreakpoint<TzXBreakpointProperties>?): String {

        val text = "Cucumber+ Example"

        // Called from coroutine threads (e.g. BackendXDebuggerManagerApi DTO conversion) in
        // 2025.3+; PSI access requires a read action there.
        return ApplicationManager.getApplication().runReadAction<String> {
            runCatching {
                val line = breakpoint?.sourcePosition?.line ?: return@runCatching text
                val vfile = breakpoint.sourcePosition?.file ?: return@runCatching text
                val project = vfile.findProject() ?: return@runCatching text
                val file = vfile.getFile(project) ?: return@runCatching text
                val doc = file.getDocument() ?: return@runCatching text

                val lineRange = doc.getLineRange(line).shrink(1, 1)
                val row = file.findElementsOfTypeInRange(lineRange, GherkinTableRow::class.java).firstOrNull()
                    ?: return@runCatching text

                if (row is GherkinTableHeaderRowImpl)
                    return@runCatching text

                val examples = row.parentOfTypeIs<GherkinExamplesBlock>(true)
                    ?: return@runCatching text

                val scenario = examples.parentOfTypeIs<GherkinScenarioOutline>(true)
                    ?: return@runCatching text

                val index = scenario.allExamples().indexOf(row)
                if (index < 0)
                    return@runCatching text

                text + " #" + (index + 1)
            }.getOrDefault(text)
        }
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

private val STRUCTURAL_LINE = Regex(
    "^(Feature|Scenario|Background|Rule|Examples?|Scenario Outline)\\s*:",
    RegexOption.IGNORE_CASE
)

private fun isLikelyStepLine(doc: Document, line: Int): Boolean {
    if (line < 0 || line >= doc.lineCount) return false
    val text = doc.charsSequence
        .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
        .trimStart()
    if (text.isEmpty() || text.startsWith("#") || text.startsWith("@") || text.startsWith("\"\"\"") || text.startsWith("|"))
        return false
    return !STRUCTURAL_LINE.containsMatchIn(text.subSequence(0, minOf(40, text.length)))
}

private fun isLikelyExampleRowLine(doc: Document, line: Int): Boolean {
    if (line < 0 || line >= doc.lineCount) return false
    val text = doc.charsSequence
        .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
        .trimStart()
    return text.startsWith("|")
}