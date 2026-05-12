package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import icons.ActionIcons
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

        // Text-based fast reject — immune to transient PSI re-parse. In 2025.3+ the backend
        // calls canPutAt again ~500ms after creation on a background thread; PSI may be
        // unavailable at that point so this layer always runs first.
        val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return false
        if (!isLikelyStepLine(doc, line)) return false

        // PSI confirmation: only a real GherkinStep is a valid breakpoint host. Rules out
        // free-form description text (Business Need / Ability / "As a user…") and translated
        // step keywords from non-English dialects that the text regex won't list exhaustively.
        // PSI unavailable → trust the text check above (we never drop a valid breakpoint).
        return isGherkinStepAtLine(project, vfile, doc, line, default = true)
    }

    override fun getDisplayText(breakpoint: XLineBreakpoint<TzXBreakpointProperties>?)
            = "Cucumber+ Step breakpoint"

    // Cucumber+ themed icons across all states so the Gherkin side keeps its identity.
    //
    // Note: TzBreakpointType hides the suspend-policy panel ({@link getVisibleStandardPanels}
    // returns an empty set), which means Cucumber+ Gherkin breakpoints effectively run with
    // SuspendPolicy.NONE. As a consequence IntelliJ asks getSuspendNoneIcon() — NOT
    // getEnabledIcon() — to render the gutter glyph for an active breakpoint. We map both
    // to the same filled green disc so the user always sees the proper "active" Cucumber+
    // glyph, regardless of which getter the platform queries.
    override fun getEnabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER
    override fun getDisabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER_DISABLED
    override fun getMutedEnabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER
    override fun getMutedDisabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER_DISABLED
    override fun getSuspendNoneIcon() = ActionIcons.BREAKPOINT_CUCUMBER
}


class TzStepExampleBreakpointType() : TzBreakpointType("tzatziki.gherkin.step.example", "Cucumber+ Step example") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        try {
            if (vfile.extension != "feature") return false

            val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return false
            if (!isLikelyExampleRowLine(doc, line)) return false

            // PSI confirmation: only rows under a GherkinExamplesBlock (i.e. the table beneath
            // "Examples:" of a Scenario Outline) accept this breakpoint. Plain data tables
            // attached to a step (e.g. `Given I have the following books: | … |`) must NOT.
            return isExamplesRowAtLine(project, vfile, doc, line, default = false)

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

    // Examples breakpoints: Cucumber+ green diamond — distinguishable from the step BP
    // (round green disc), still in the Cucumber+ green palette. The platform's
    // Db_method_breakpoint we tried first was a RED diamond, which clashed with the
    // green identity; the previous Db_field_breakpoint (a red "eye") was even worse.
    override fun getEnabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER_EXAMPLE
    override fun getDisabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER_EXAMPLE_DISABLED
    override fun getMutedEnabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER_EXAMPLE
    override fun getMutedDisabledIcon() = ActionIcons.BREAKPOINT_CUCUMBER_EXAMPLE_DISABLED
    override fun getSuspendNoneIcon() = ActionIcons.BREAKPOINT_CUCUMBER_EXAMPLE

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

// Recognised Gherkin structural keywords (English aliases included — "Business Need" and
// "Ability" are valid synonyms for "Feature:" in standard Gherkin, "Example" / "Scenarios"
// / "Scenario Template" are valid synonyms for "Scenario" / "Examples" / "Scenario Outline").
private val STRUCTURAL_LINE = Regex(
    "^(Feature|Business Need|Ability|" +
            "Scenario|Example|" +
            "Scenario Outline|Scenario Template|" +
            "Background|Rule|Examples?|Scenarios)\\s*:",
    RegexOption.IGNORE_CASE
)

/**
 * True when the line at [line] is parsed by the Gherkin PSI as a real [GherkinStep].
 *
 * Robust against free-form description text (Business Need / Ability / "As a user, I want…")
 * which would otherwise fool the text regex into accepting a breakpoint there. When PSI is
 * not available (e.g. canPutAt called on a background thread before reparse completes),
 * returns [default] so we don't drop a freshly-created or migrated breakpoint by accident.
 */
private fun isGherkinStepAtLine(
    project: Project,
    vfile: VirtualFile,
    doc: Document,
    line: Int,
    default: Boolean
): Boolean = runCatching {
    ReadAction.compute<Boolean, RuntimeException> {
        val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@compute default
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd   = doc.getLineEndOffset(line)
        val lineText  = doc.charsSequence.subSequence(lineStart, lineEnd)
        val firstNonWs = lineText.indexOfFirst { !it.isWhitespace() }
        if (firstNonWs < 0) return@compute false
        var element: PsiElement? = psiFile.findElementAt(lineStart + firstNonWs)
        while (element != null) {
            if (element is GherkinStep) return@compute true
            // Stop walking once we've climbed beyond the line — any GherkinStep above us
            // would belong to a different (preceding) line.
            if (element.textRange.startOffset < lineStart) return@compute false
            element = element.parent
        }
        false
    }
}.getOrDefault(default)

private fun isLikelyStepLine(doc: Document, line: Int): Boolean {
    if (line < 0 || line >= doc.lineCount) return false
    val text = doc.charsSequence
        .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
        .trimStart()
    if (text.isEmpty() || text.startsWith("#") || text.startsWith("@") || text.startsWith("\"\"\"") || text.startsWith("|"))
        return false
    return !STRUCTURAL_LINE.containsMatchIn(text.subSequence(0, minOf(40, text.length)))
}

/**
 * True when the pipe-row at [line] is a `GherkinTableRow` (excluding the header row)
 * inside a `GherkinExamplesBlock`. Data tables attached to a step return false; the
 * Examples header row returns false. When PSI is unavailable the result falls back
 * to [default].
 */
private fun isExamplesRowAtLine(
    project: Project,
    vfile: VirtualFile,
    doc: Document,
    line: Int,
    default: Boolean
): Boolean = runCatching {
    ReadAction.compute<Boolean, RuntimeException> {
        val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@compute default
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd   = doc.getLineEndOffset(line)
        val firstNonWs = doc.charsSequence.subSequence(lineStart, lineEnd).indexOfFirst { !it.isWhitespace() }
        if (firstNonWs < 0) return@compute false
        var element: PsiElement? = psiFile.findElementAt(lineStart + firstNonWs)
        var row: GherkinTableRow? = null
        while (element != null) {
            if (row == null && element is GherkinTableRow) row = element
            if (element is GherkinExamplesBlock) {
                // Header row is also a GherkinTableRow → exclude it explicitly.
                return@compute row != null && row !is GherkinTableHeaderRowImpl
            }
            element = element.parent
        }
        false
    }
}.getOrDefault(default)

private fun isLikelyExampleRowLine(doc: Document, line: Int): Boolean {
    if (line < 0 || line >= doc.lineCount) return false
    val text = doc.charsSequence
        .subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
        .trimStart()
    return text.startsWith("|")
}