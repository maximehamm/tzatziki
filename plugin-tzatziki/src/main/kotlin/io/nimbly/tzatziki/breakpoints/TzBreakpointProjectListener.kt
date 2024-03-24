package io.nimbly.tzatziki.breakpoints

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.ui.DebuggerColors
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.cucumber.psi.*
import java.net.URI

const val CUCUMBER_FAKE_EXPRESSION = "\"Cucumber+\"!=null"

class TzBreakpointProjectListener : StartupActivity {

    companion object {
        private val CUCUMBER_EXECUTION_POINT: Key<TzExecutionPosition> = Key.create("CUCUMBER_EXECUTION_POSITION")

        fun Project.cucumberExecutionPoint(): TzExecutionPosition {
            var p = this.getUserData(CUCUMBER_EXECUTION_POINT)
            if (p == null) {
                p = TzExecutionPosition()
                this.putUserData(CUCUMBER_EXECUTION_POINT, p)
            }
            return p
        }
    }

    data class TzExecutionPosition(
        var featurePath: String? = null,
        var lineNumber: Int? = null,
        var exampleNumber: Int? = null
    ) {
        fun clear() {
            featurePath = null
            lineNumber = null
            exampleNumber = null
        }
    }

    override fun runActivity(project: Project) {

        val highlighted = mutableListOf<RangeHighlighter>()
        var markupModel: MarkupModel? = null

        project.messageBus
            .connect()
            .subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {

                private var listener: ProcessListener? = null

                override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {

                    project.cucumberExecutionPoint().clear()
                    val listener = object : ProcessListener {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {

                            val regex = Regex(" locationHint = '([^']+)")
                            val filePathAndPosition = regex.find(event.text)?.groupValues?.get(1)
                            if (filePathAndPosition != null) {
                                if (filePathAndPosition.lastIndexOf(':') < 1) return
                                val filePath = URI(filePathAndPosition.substringBeforeLast(':')).path
                                val fileLine = filePathAndPosition.substringAfterLast(':').toIntOrNull() ?: return

                                val p = project.cucumberExecutionPoint()
                                p.featurePath = filePath
                                p.lineNumber = fileLine
                            }
                            else {

                                val regex2 = Regex(" name = 'Example #(\\d+)'")
                                val exampleNumber = regex2.find(event.text)?.groupValues?.get(1)?.toInt()
                                if (exampleNumber != null) {

                                    val p = project.cucumberExecutionPoint()
                                    p.exampleNumber = exampleNumber
                                }
                            }
                        }
                    }
                    handler.addProcessListener(listener)
                    this.listener = listener
                }

                override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
                    removeBreakpointHighlighter(markupModel, highlighted)
                }

            })

        var modificationInProgress = false
        var addInProgress = false
        project.messageBus
            .connect()
            .subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {

                override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
                    if (modificationInProgress)
                        return
                    try {
                        modificationInProgress = true
                        refresh(breakpoint, EAction.CHANGED)
                    } finally {
                        modificationInProgress = false
                    }
                }

                override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
                    if (addInProgress)
                        return
                    try {
                        addInProgress = true
                        refresh(breakpoint, EAction.ADDED)
                    } finally {
                        addInProgress = false
                    }
                }

                override fun breakpointRemoved(breakpoint: XBreakpoint<*>) =
                    refresh(breakpoint, EAction.REMOVED)

                override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) = Unit
            })

        project.messageBus
            .connect()
            .subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {

                override fun processStarted(debugProcess: XDebugProcess) {
                    if (debugProcess !is JavaDebugProcess) return
                    debugProcess.debuggerSession.contextManager.addListener(object : DebuggerContextListener {

                        override fun changeEvent(newContext: DebuggerContextImpl, event: DebuggerSession.Event?) {

                            // Check if running a cucumber test
                            if ("Cucumber Java" != RunManager.getInstance(project).selectedConfiguration?.type?.displayName)
                                return

                            // Check current cucumber step...
                            val executionPoint = project.cucumberExecutionPoint()
                            if (executionPoint.featurePath == null) return

                            // Search if we stopped at a gherkin breakpoint
                            val file = newContext.sourcePosition?.file ?: return
                            val offset = newContext.sourcePosition.offset
                            val step = Tzatziki.findSteps(file.virtualFile, offset)
                                .filter { it.containingFile.virtualFile.path == executionPoint.featurePath }
                                .firstOrNull { it.getDocumentLine() == executionPoint.lineNumber!! - 1 }
                                ?: return

                            // Find step's breakpoint
                            val breakpoint = step.findBreakpoint()
                                ?: return

                            // If breakpoint in deactivate, let's resume debugger
                            if (!breakpoint.isEnabled) {
                                debugProcess.debuggerSession.xDebugSession?.resume()
                                return
                            }

                            // Select step breakpoint
                            FileEditorManager.getInstance(project).getAllEditors(step.containingFile.virtualFile)
                                .filterIsInstance<TextEditor>()
                                .forEach { editor ->

                                    val doc = step.getDocument() ?: return@forEach

                                    val line = doc.getLineNumber(step.textOffset)
                                    val start = doc.getLineStartOffset(line)
                                    val end = doc.getLineEndOffset(line)

                                    markupModel = editor.editor.markupModel

                                    removeBreakpointHighlighter(markupModel, highlighted)

                                    highlighted += editor.editor.markupModel.addRangeHighlighter(
                                        DebuggerColors.EXECUTIONPOINT_ATTRIBUTES,
                                        start, end,
                                        DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                        HighlighterTargetArea.LINES_IN_RANGE
                                    )

                                    val exampleNumber = project.cucumberExecutionPoint().exampleNumber
                                    if (step.stepHolder is GherkinScenarioOutline) {

                                        val examples = step.findExampleTable()
                                        val row = examples?.dataRows?.getOrNull(exampleNumber ?: 0)

                                        if (row != null) {
                                            val exline = doc.getLineNumber(row.textOffset)
                                            highlighted += editor.editor.markupModel.addRangeHighlighter(
                                                DebuggerColors.EXECUTIONPOINT_ATTRIBUTES,
                                                doc.getLineStartOffset(exline),
                                                doc.getLineEndOffset(exline),
                                                DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                                HighlighterTargetArea.LINES_IN_RANGE
                                            )
                                        }
                                    }

                                    val vp = editor.editor.offsetToLogicalPosition(step.textOffset)
                                    editor.editor.scrollingModel.scrollTo(vp, ScrollType.MAKE_VISIBLE)
                                }
                        }
                    })
                }

                override fun processStopped(debugProcess: XDebugProcess) {
                    removeBreakpointHighlighter(markupModel, highlighted)
                }
            })

    }

    fun removeBreakpointHighlighter(markupModel: MarkupModel?, highlighted: MutableList<RangeHighlighter>) {
        markupModel ?: return

        val copy = highlighted.toList()
        highlighted.clear()

        ApplicationManager.getApplication().invokeLater {
            copy.forEach {
                markupModel.removeHighlighter(it)
                highlighted.remove(it)
            }
        }

    }

    private fun refresh(breakpoint: XBreakpoint<*>, action: EAction) {

        if (breakpoint.sourcePosition?.file?.fileType == GherkinFileType.INSTANCE) {
            refreshGherkin(breakpoint, action)
        } else {
            refreshCode(breakpoint, action)
        }
    }

    private fun refreshGherkin(gherkinBreakpoint: XBreakpoint<*>, action: EAction) {

        val vfile = gherkinBreakpoint.sourcePosition?.file ?: return
        val line = gherkinBreakpoint.sourcePosition?.line ?: return

        val project = ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .firstOrNull() { vfile.getFile(it) != null }
            ?: return

        val step = findStep(vfile, project, line)
            ?: return

        val stepDefinitions = step.findCucumberStepDefinitions()
        if (stepDefinitions.isEmpty())
            return

        stepDefinitions.first().element

        val codeBreakPointElt = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findBestPositionToAddBreakpoint(stepDefinitions)
        } ?: return

        val allCodeBreakpoints = Tzatziki().extensionList.firstNotNullOfOrNull {
            val offset = codeBreakPointElt.first.containingFile.getDocument()?.getLineStartOffset(codeBreakPointElt.second)
            it.findStepsAndBreakpoints(
                codeBreakPointElt.first.containingFile.virtualFile,
                offset
            )
        }

        if (action == EAction.ADDED) {

            if (allCodeBreakpoints?.second?.isEmpty() == true) {
              (XDebuggerUtil.getInstance() as? XDebuggerUtilImpl)?.toggleAndReturnLineBreakpoint(
                    project,
                    codeBreakPointElt.first.containingFile.virtualFile,
                    codeBreakPointElt.second, false)
                    ?.then { it: XLineBreakpoint<out XBreakpointProperties<*>> ->
                        it.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
                        (it.properties as JavaLineBreakpointProperties).lambdaOrdinal =  -1
                    }
            }
            allCodeBreakpoints?.second?.forEach { b ->
                b.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
            }
        } else if (action == EAction.REMOVED) {

            allCodeBreakpoints?.second?.forEach { b ->
                XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
            }
        } else if (action == EAction.CHANGED) {

            allCodeBreakpoints?.second?.forEach { b ->
                b.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
            }
        }
    }

    private fun refreshCode(breakpoint: XBreakpoint<*>, action: EAction) {

        val pair = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findStepsAndBreakpoints(
                breakpoint.sourcePosition?.file,
                breakpoint.sourcePosition?.offset)
        } ?: return

        val steps = pair.first
        val codeBreakpoints = pair.second
        val createdFromCode = breakpoint.conditionExpression == null

        if (steps.isNotEmpty() && breakpoint.conditionExpression == null)
            breakpoint.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)

        steps.forEach { step ->

            val documentLine = step.getDocumentLine()
                ?: return@forEach

            if (action == EAction.ADDED && codeBreakpoints.size == 1) {

                if (createdFromCode) {

                    val oldStepBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
                        .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                        .filter { it.sourcePosition?.line == step.getDocumentLine() }

                    if (oldStepBreakpoints.isEmpty()) {
                        toggleBreakpoint(step, documentLine)
                        step.updatePresentation(codeBreakpoints)
                    }
                }
            }
            else if (action == EAction.REMOVED && codeBreakpoints.size == 0) {
                deleteBreakpoints(step)
            }
            else {
                step.updatePresentation(codeBreakpoints)
            }
        }
    }

    private fun toggleBreakpoint(step: GherkinStep, documentLine: Int) {
        (XDebuggerUtil.getInstance() as? XDebuggerUtilImpl)?.toggleAndReturnLineBreakpoint(
            step.project,
            step.containingFile.virtualFile,
            documentLine, false)
            ?.then { it: XLineBreakpoint<out XBreakpointProperties<*>> ->
                it.conditionExpression = null
            }
    }

    private fun deleteBreakpoints(step: GherkinStep) {
        val oldBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
            .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
            .filter { it.sourcePosition?.line == step.getDocumentLine() }
        oldBreakpoints.forEach { b ->
            XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
        }
    }

}

enum class EAction { CHANGED, ADDED, REMOVED }
