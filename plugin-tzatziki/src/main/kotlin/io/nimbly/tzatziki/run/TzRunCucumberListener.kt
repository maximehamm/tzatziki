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

    private val REGEX = Regex(" locationHint = '(?:file:///)?((?:[A-Z]:|/)?[^:]+):(\\d+)'(?: name = 'Example #(\\d+)')?")

    private var stopRequested = false

    data class TzExecutionTracker(

        var featurePath: String? = null,
        var lineNumber: Int? = null,
        var exampleLine: Int? = null,
        var lastSuiteLine: Int? = null,

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

                val values = REGEX.find(text)?.groupValues ?: return

                val path = values[1].trim()
                val line = values[2].toInt()
                val isExample = values[3].isNotEmpty()

                if (!isExample) {
                    LOG.info("C+ onTextAvailable suite=$isSuiteEvent path=$path line=$line")
                    tracker.featurePath = path
                    tracker.lineNumber = line - 1
                    if (isSuiteEvent) {
                        tracker.lastSuiteLine = line - 1
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

                val lineStart = tracker.lastSuiteLine ?: run {
                    LOG.warn("C+ guide: lastSuiteLine unknown")
                    return
                }
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
    private val myLine1: Int,
    private val myLine2: Int,
    private val myColor: Color,
    private val myTooltip: String) : ActiveGutterRenderer {
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
