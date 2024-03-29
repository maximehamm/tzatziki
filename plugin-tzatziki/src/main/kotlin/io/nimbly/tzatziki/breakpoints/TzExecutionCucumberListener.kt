package io.nimbly.tzatziki.breakpoints

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.ui.DebuggerColors
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.toPath
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

class TzExecutionCucumberListener : StartupActivity {

    private val LOG = logger<TzExecutionCucumberListener>()
    private val REGEX = Regex(" locationHint = '(?:file://)?([^:]+):(\\d+)'(?: name = 'Example #(\\d+)')?")

    data class TzExecutionTracker(
        var featurePath: String? = null,
        var lineNumber: Int? = null,
        var exampleLine: Int? = null,

        val highlighters: MutableList<RangeHighlighter> = mutableListOf(),
        var highlightersModel: MarkupModel? = null
    ) {

        fun clear() {
            featurePath = null
            lineNumber = null
            exampleLine = null
        }

        fun removeHighlighters() {

            val model = this.highlightersModel ?: return
            val copy = this.highlighters.toList() ?: return

            this.highlighters.clear()

            ApplicationManager.getApplication().invokeLater {
                copy.forEach {
                    model.removeHighlighter(it)
                }
            }
        }

        fun findFile(): VirtualFile? {
            var p = featurePath ?: return null
            if (p.matches("^/[A-Z]:.*".toRegex()))
                p = p.substring(1)
            val toPath = p.toPath()
            return LocalFileSystem.getInstance().findFileByNioFile(toPath)
        }
    }

    override fun runActivity(project: Project) {

        project.messageBus
            .connect()
            .subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {

                private var listener: ProcessListener? = null
                private val highlights = mutableListOf<Pair<MarkupModelEx, RangeHighlighter>>()
                private val color = EditorColorsManager.getInstance().globalScheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES).backgroundColor

                override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {

                    if (!TOGGLE_CUCUMBER_PL)
                        return
                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - processStarting")

                    val tracker = project.cucumberExecutionTracker()
                    tracker.clear()

                    val listener = object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {

                            if (!TOGGLE_CUCUMBER_PL)
                                return

                            val values = REGEX.find(event.text)?.groupValues
                                ?:return

                            println(event.text)
                            LOG.debug("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - " + event.text)

                            val path = values[1].trim()
                            var line = values[2].toInt()
                            val isExample = values[3].isNotEmpty()

                            if (!isExample) {

                                LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - filePath = $path line $line")
                                val p = project.cucumberExecutionTracker()
                                p.featurePath = path
                                p.lineNumber = line - 1
                            }
                            else {

                                LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - exampleLine = $line")

                                val p = project.cucumberExecutionTracker()
                                p.exampleLine = line
                            }

                            // Highlight gutter
                            val executionPoint = project.cucumberExecutionTracker()
                            val vfile = executionPoint.findFile() ?: return
                            ApplicationManager.getApplication().invokeLater {

                                FileEditorManager.getInstance(project)
                                    .getEditors(vfile)
                                    .filterIsInstance<TextEditor>()
                                    .map { it.editor }
                                    .forEach { editor ->

                                        val markupModel = editor.markupModel as? MarkupModelEx
                                            ?: return@forEach

                                        if (line == (editor.document.lineCount))
                                            line ++

                                        highlights += highlighterExPair(markupModel, editor, line)
                                }

                            }

                        }
                    }
                    handler.addProcessListener(listener)
                    this.listener = listener
                }

                override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {

                    if (!TOGGLE_CUCUMBER_PL)
                        return
                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - processTerminated")

                    project.cucumberExecutionTracker().removeHighlighters()

                    // Just for fun to show line going to the buttom
                    ApplicationManager.getApplication().invokeLater {
                        highlights.forEach {
                            it.first.removeHighlighter(it.second)
                        }
                    }
                }

                private fun highlighterExPair(
                    markupModel: MarkupModelEx,
                    editor: Editor,
                    line: Int
                ) = markupModel to markupModel.addRangeHighlighterAndChangeAttributes(
                    null,
                    0,
                    editor.document.textLength,
                    DiffDrawUtil.LST_LINE_MARKER_LAYER + 1,
                    HighlighterTargetArea.LINES_IN_RANGE,
                    false
                ) { it: RangeHighlighterEx ->
                    it.isGreedyToLeft = true; it.isGreedyToRight = true
                    it.lineMarkerRenderer = MyGutterRenderer(
                        0,
                        line,
                        color, //JBUI.CurrentTheme.RunWidget.RUNNING_BACKGROUND,
                        "Cucumber+ test progress"
                    )
                }
            })
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