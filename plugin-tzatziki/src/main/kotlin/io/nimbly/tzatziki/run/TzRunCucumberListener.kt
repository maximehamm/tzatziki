package io.nimbly.tzatziki.run

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.actions.isShowProgressionGuide
import io.nimbly.tzatziki.editor.BREAKPOINT_EXAMPLE
import io.nimbly.tzatziki.editor.BREAKPOINT_STEP
import io.nimbly.tzatziki.testdiscovery.TzTestRegistry
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent

private val CUCUMBER_EXECUTION_POINT: Key<TzExecutionCucumberListener.TzExecutionTracker> = Key.create("CUCUMBER_EXECUTION_POSITION")

fun Project.cucumberExecutionTracker(): TzExecutionCucumberListener.TzExecutionTracker {

    var p = this.getUserData(CUCUMBER_EXECUTION_POINT)
    if (p == null) {
        p = TzExecutionCucumberListener.TzExecutionTracker()
        this.putUserData(CUCUMBER_EXECUTION_POINT, p)
    }
    return p
}

class TzExecutionCucumberListener(private val project: Project) : ExecutionListener {

    private val LOG = logger<TzExecutionCucumberListener>()

    /*
     Windows :
       ##teamcity[testStarted timestamp = '2024-04-03T06:52:43.058+0000' locationHint = 'file:///C:/projects/cucumber-discovery/src/test/resources/org/maxime/cucumber/Wallet.feature:9' captureStandardOutput = 'true' name = 'Je créé un portefeuille avec 100.0 €']
     MacOS :
       ##teamcity[testSuiteStarted timestamp = '2024-04-03T07:14:18.444+0000' locationHint = 'file:///Users/maxime/Development/projects-nimbly/cucumber-discovery/src/test/resources/org/maxime/cucumber/Wallet.feature:1' name = 'Manipulation d|'un portefeuille']
     */

    private val LOC_REGEX  = Regex("locationHint = '(?:file:///)?((?:[A-Z]:|/)?[^:]+):(\\d+)'")
    private val NAME_REGEX = Regex("name = '([^']+)'")

    /**
     * True when the line at index [lineIndex] (0-based) of the `.feature` file tracked by
     * [tracker] is a Gherkin scenario-header line. Uses the PSI — `GherkinScenario` /
     * `GherkinScenarioOutline` — so any of Gherkin's ~70 supported localizations works
     * without us having to maintain a keyword regex per language.
     *
     * Used to detect the OUTERMOST scenario suite from a TeamCity `testSuiteStarted` event
     * whose `name` attribute is just the scenario title without its `Scenario:` /
     * `Scenario Outline:` prefix (or its translation).
     */
    private fun isScenarioHeaderLine(tracker: TzExecutionTracker, lineIndex: Int): Boolean {
        if (lineIndex < 0) return false
        val vfile = tracker.findFile() ?: return false
        return com.intellij.openapi.application.ReadAction.compute<Boolean, RuntimeException> {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vfile)
                ?: return@compute false
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                .getDocument(vfile) ?: return@compute false
            if (lineIndex >= doc.lineCount) return@compute false
            val offset = doc.getLineStartOffset(lineIndex)
            // Walk the leaf at the line start up to its closest scenario / outline parent.
            // `findElementAt` returns null for whitespace at the start of the line; skip
            // to the first non-whitespace char if needed.
            val lineEnd = doc.getLineEndOffset(lineIndex)
            var probe = offset
            while (probe < lineEnd && doc.charsSequence[probe].isWhitespace()) probe++
            val element = psiFile.findElementAt(probe) ?: return@compute false
            val owner = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                element,
                org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline::class.java,
                org.jetbrains.plugins.cucumber.psi.GherkinScenario::class.java
            ) ?: return@compute false
            // The element must be ON the header line itself, not nested inside one of the
            // scenario's steps / examples block.
            doc.getLineNumber(owner.textRange.startOffset) == lineIndex
        }
    }

    private var stopRequested = false

    data class TzExecutionTracker(

        var featurePath: String? = null,
        var lineNumber: Int? = null,
        var exampleLine: Int? = null,
        var lastSuiteLine: Int? = null,
        // Line of the OUTERMOST scenario currently being executed — `Scenario:` or
        // `Scenario Outline:` header. Stays stable across the inner suites of an outline
        // (`Examples`, `Example #N`, …) so the gutter progression bar can anchor itself
        // at the scenario header instead of jumping to each row of the examples table.
        var scenarioStartLine: Int? = null,

        var runGeneration: Long = 0L,

        val highlighters: MutableList<RangeHighlighter> = mutableListOf(),
        val progressionGuides: MutableList<Pair<MarkupModelEx, RangeHighlighterEx>> = mutableListOf(),

        var highlightersModel: MarkupModel? = null
    ) {

        fun clear() {
            featurePath = null
            lineNumber = null
            exampleLine = null
            lastSuiteLine = null
            scenarioStartLine = null
        }

        fun removeProgressionGuides() {
            if (ApplicationManager.getApplication().isDispatchThread) {
                progressionGuides.forEach { it.first.removeHighlighter(it.second) }
                progressionGuides.clear()
            } else {
                ApplicationManager.getApplication().invokeLater {
                    progressionGuides.forEach { it.first.removeHighlighter(it.second) }
                    progressionGuides.clear()
                }
            }
        }

        fun removeHighlighters() {

            val model = this.highlightersModel ?: return
            val copy = this.highlighters.toList()

            this.highlighters.clear()

            ApplicationManager.getApplication().invokeLater {
                copy.forEach {
                    model.removeHighlighter(it)
                }
            }
        }

        fun findFile(): VirtualFile? {
            var p = featurePath ?: return null
            if (!p.matches("^[A-Z]:.*".toRegex()))
                p = "/$p"
            return LocalFileSystem.getInstance().findFileByPath(p)
        }
    }

    override fun processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        stopRequested = true
        super.processTerminating(executorId, env, handler)
    }

    override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {

        if (!TOGGLE_CUCUMBER_PL)
            return
        LOG.info("C+ processStarting")

        val tracker = project.cucumberExecutionTracker()
        tracker.clear()
        tracker.removeProgressionGuides()
        tracker.removeHighlighters()
        val generation = ++tracker.runGeneration

        var first = true

        stopRequested = false
        val listener = object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {

                if (!TOGGLE_CUCUMBER_PL)
                    return

                if (stopRequested)
                    return

                val text = event.text
                val isSuiteEvent = text.contains("[testSuiteStarted ")
                val isTestEvent  = text.contains("[testStarted ")
                if (!isSuiteEvent && !isTestEvent)
                    return

                val loc = LOC_REGEX.find(text) ?: return
                val path = loc.groupValues[1].trim()
                val line = loc.groupValues[2].toInt()
                val name = NAME_REGEX.find(text)?.groupValues?.get(1).orEmpty()
                val isExample = name.startsWith("Example #")

                if (!isExample) {
                    LOG.info("C+ onTextAvailable suite=$isSuiteEvent path=$path line=$line name='$name'")
                    tracker.featurePath = path
                    tracker.lineNumber = line - 1
                    if (isSuiteEvent) {
                        tracker.lastSuiteLine = line - 1
                        // Anchor the progression bar at the OUTERMOST scenario header.
                        // Cucumber emits the suite NAME without its Gherkin prefix
                        // ("Vérifier le score" instead of "Scenario Outline: Vérifier…")
                        // so we look at the actual line content in the .feature file
                        // instead. Lines starting with `Scenario:` / `Scenario Outline:`
                        // (or their localized equivalents) anchor the bar; Feature lines,
                        // Examples blocks and Example #N rows never do.
                        if (isScenarioHeaderLine(tracker, line - 1)) {
                            tracker.scenarioStartLine = line - 1
                        }
                    }
                } else {
                    LOG.info("C+ onTextAvailable example line=$line")
                    tracker.exampleLine = line
                }

                if (first) {
                    first = false
                    LOG.info("C+ clearing previous highlights")
                    ApplicationManager.getApplication().invokeLater {
                        TzTestRegistry.clearHighlighters()
                    }
                }

                if (!isTestEvent)
                    return

                if (!isShowProgressionGuide())
                    return

                val vfile = tracker.findFile() ?: run {
                    LOG.warn("C+ guide: findFile null for '${tracker.featurePath}'")
                    return
                }

                // Prefer the OUTERMOST scenario line as the bar anchor so it doesn't
                // jump to each Example #N row of an outline. Fall back to lastSuiteLine
                // for simple scenarios where scenarioStartLine wasn't set (e.g. older
                // cucumber-jvm output that emits a different suite-name format).
                val lineStart = tracker.scenarioStartLine
                    ?: tracker.lastSuiteLine
                    ?: run {
                        LOG.warn("C+ guide: scenarioStartLine / lastSuiteLine unknown")
                        return
                    }
                // `lineStart` is stored 0-indexed (line - 1 at the suite event) while
                // `lineEnd` is left 1-indexed on purpose: paintSimpleRange treats line2 as
                // an EXCLUSIVE upper bound, so passing the 1-indexed line number makes the
                // bar cover line2-1 (the current step) inclusively. Aligning both to
                // 0-indexed dropped the last covered line and visually shortened the bar
                // by one row.
                val lineEnd = line

                val captureGeneration = generation
                LOG.info("C+ guide: lineStart=$lineStart lineEnd=$lineEnd isExample=$isExample")

                ApplicationManager.getApplication().invokeLater({
                    if (tracker.runGeneration != captureGeneration) return@invokeLater
                    val editors = FileEditorManager.getInstance(project).getEditors(vfile)
                        .filterIsInstance<TextEditor>()
                    if (editors.isEmpty()) {
                        LOG.warn("C+ guide: no TextEditor open for $vfile")
                    }
                    editors.forEach { textEditor ->
                        val markupModel = textEditor.editor.markupModel as? MarkupModelEx ?: return@forEach
                        val docLines = textEditor.editor.document.lineCount
                        val adjustedEnd = if (lineEnd >= docLines) docLines else lineEnd
                        tracker.progressionGuides += highlightProgression(markupModel, lineStart, adjustedEnd, isExample)
                    }
                }, ModalityState.any())
            }
        }
        handler.addProcessListener(listener)
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {

        if (!TOGGLE_CUCUMBER_PL)
            return
        LOG.info("C+ processTerminated")

        project.cucumberExecutionTracker().removeProgressionGuides()
        project.cucumberExecutionTracker().removeHighlighters()
    }

    private fun highlightProgression(
        markupModel: MarkupModelEx,
        lineStart: Int,
        lineEnd: Int,
        isExample: Boolean
    ): Pair<MarkupModelEx, RangeHighlighterEx> = markupModel to markupModel.addRangeHighlighterAndChangeAttributes(
        null,
        0,  markupModel.document.textLength,
        DiffDrawUtil.LST_LINE_MARKER_LAYER + 1,
        HighlighterTargetArea.LINES_IN_RANGE,
        false
    ) { it: RangeHighlighterEx ->
        it.isGreedyToLeft = true
        it.isGreedyToRight = true

        it.lineMarkerRenderer = MyGutterRenderer(
            lineStart,
            lineEnd,
            if (isExample)
                EditorColorsManager.getInstance().globalScheme.getAttributes(BREAKPOINT_EXAMPLE).backgroundColor
            else
                EditorColorsManager.getInstance().globalScheme.getAttributes(BREAKPOINT_STEP).backgroundColor,
            "Cucumber+ test progress"
        )
    }
}

class MyGutterRenderer(
    line1: Int,
    line2: Int,
    private val myColor: Color,
    private val myTooltip: String) : ActiveGutterRenderer {
    // Scenario Outline can emit (lineStart, lineEnd) with lineEnd < lineStart while the
    // current example row sits above the outline header — paintSimpleRange does not draw
    // anything for an inverted range, which is why the progress bar disappeared on
    // outlines. Normalise here so the gutter always gets a valid (top, bottom) pair.
    private val myLine1: Int = minOf(line1, line2)
    private val myLine2: Int = maxOf(line1, line2)
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        LineStatusMarkerDrawUtil.paintSimpleRange(g, editor, myLine1, myLine2, myColor)
    }
    override fun getTooltipText(): String {
        return myTooltip
    }
    override fun canDoAction(e: MouseEvent): Boolean {
        return false
    }
    override fun doAction(editor: Editor, e: MouseEvent) {
    }
    override fun getAccessibleName(): String {
        return myTooltip
    }
}
