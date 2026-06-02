package io.nimbly.tzatziki.run

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.RunManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.ui.DebuggerColors
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.editor.BREAKPOINT_EXAMPLE
import io.nimbly.tzatziki.editor.BREAKPOINT_STEP
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzRunCodeListener(private val project: Project) : XDebuggerManagerListener {

    private val LOG = logger<TzRunCodeListener>()

    override fun processStarted(debugProcess: XDebugProcess) {

        LOG.info("C+ XDebuggerManager.TOPIC - processStarted : " + debugProcess::class.java)
        if (!TOGGLE_CUCUMBER_PL)
            return

        if (debugProcess !is JavaDebugProcess) {
            // Non-Java debug processes (Python pydevd, JS Node, …) can't use the Java
            // DebuggerContextListener used below. We drive the SAME "skip the muted step"
            // behaviour from the generic XDebugSession pause callback: on each pause, if the
            // current cucumber step's Gherkin breakpoint is muted, resume past it. The shared
            // code breakpoint stays enabled (anyEnabled); the per-scenario skip is decided here
            // at runtime — exactly like the Java path.
            debugProcess.session.addSessionListener(object : com.intellij.xdebugger.XDebugSessionListener {
                override fun sessionPaused() {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        runCatching { handleGenericPause(debugProcess) }.onFailure {
                            if (it !is com.intellij.openapi.progress.ProcessCanceledException)
                                LOG.warn("C+ generic pause handling failed", it)
                        }
                    }
                }
            })
            return
        }
        if (!isJavaPresent()) return
        debugProcess.debuggerSession.contextManager.addListener(object : DebuggerContextListener {

            override fun changeEvent(newContext: DebuggerContextImpl, event: DebuggerSession.Event?) {

                LOG.info("C+ XDebuggerManager.TOPIC - changeEvent = $event")
                if (event != DebuggerSession.Event.PAUSE)
                    return

                // Check if running a cucumber test
                val displayName = RunManager.getInstance(project).selectedConfiguration?.type?.displayName
                LOG.debug("C+ XDebuggerManager.TOPIC - displayName = $displayName")
                if ("Cucumber Java" != displayName)
                    return

                // Check current cucumber step...
                val executionPoint = project.cucumberExecutionTracker()
                if (executionPoint.featurePath == null) return
                LOG.debug("C+ XDebuggerManager.TOPIC - featurePath = " + executionPoint.featurePath)

                // Search the step
                val vfile = executionPoint.findFile() ?: return
                val step = findStep(vfile, project, executionPoint.lineNumber!!)
                    ?: return
                LOG.debug("C+ XDebuggerManager.TOPIC - Step found")

                // Check that the breakpoint is one Cucumber+ owns (now identified by type,
                // see TzCucumberCodeBreakpointType — replaces the legacy fake-condition check).
                val codeBreakpoint = newContext.sourcePosition?.elementAt?.findBreakpoint()
                if (codeBreakpoint?.isCucumberSyncBreakpoint() != true) {

                    // Highlight ghekin step (and also examples if)
                    highlightExecutionPosition(step)
                    return
                }

                // Find step's breakpoint
                val breakpoint = step.findBreakpoint()
                LOG.debug("C+ XDebuggerManager.TOPIC - Breakpoint found")

                // Resume execution if no breakpoint or if deactivated
                if (breakpoint == null || !breakpoint.isEnabled) {

                    LOG.debug("C+ XDebuggerManager.TOPIC - Breakpoint enabled " + breakpoint?.isEnabled)
                    debugProcess.debuggerSession.xDebugSession?.resume()
                    return
                }

                // Resume execution if step contains example and if line has no breakpoint or if deactivated
                val scenarioOutline = step.stepHolder as? GherkinScenarioOutline
                if (scenarioOutline != null) {

                    val example = scenarioOutline.getExample(executionPoint.exampleLine)
                    if (example != null) {

                        LOG.debug("C+ XDebuggerManager.TOPIC - Example found")
                        val exampleBreakpoint = example.findBreakpoint()
                        if (exampleBreakpoint == null || !exampleBreakpoint.isEnabled) {
                            debugProcess.debuggerSession.xDebugSession?.resume()
                            return
                        }
                    }
                }

                // Highlight ghekin step (and also examples if)
                highlightExecutionPosition(step)
            }
        })
    }

    /**
     * Generic (non-Java) equivalent of the Java DebuggerContextListener logic above: on a debug
     * pause, decide whether to skip (resume) because the current cucumber step is muted.
     * Used for Python (pydevd) and JS (Node) debug sessions.
     */
    private fun handleGenericPause(debugProcess: XDebugProcess) {
        val session = debugProcess.session

        // Check we are in a cucumber run with a known current step (tracker fed from the
        // TeamCity output by TzExecutionCucumberListener — works for cucumber-jvm, cucumber-js
        // and our behave formatter).
        val executionPoint = project.cucumberExecutionTracker()
        if (executionPoint.featurePath == null) return
        val vfile = executionPoint.findFile() ?: return
        val line = executionPoint.lineNumber ?: return
        val step = findStep(vfile, project, line) ?: return

        val pos = session.currentStackFrame?.sourcePosition ?: return

        // Act only when the pause sits at the step-def Cucumber+ syncs to:
        //  - JS/TS: the code breakpoint is our Cucumber+ type (isCucumberSyncBreakpoint()).
        //  - Python: the code breakpoint is the NATIVE python-line type, so instead verify the
        //    paused position is the resolved step-def body line for this step.
        val codeBp = breakpointAt(pos)
        val ours = codeBp?.isCucumberSyncBreakpoint() == true || isSyncedStepDefPosition(step, pos)
        if (!ours) {
            highlightExecutionPosition(step)
            return
        }

        // Skip (resume) when the step's Gherkin breakpoint is missing or muted.
        val breakpoint = step.findBreakpoint()
        if (breakpoint == null || !breakpoint.isEnabled) {
            session.resume()
            return
        }

        // Scenario Outline: skip when the current example row's breakpoint is missing/muted.
        val outline = step.stepHolder as? GherkinScenarioOutline
        if (outline != null) {
            val example = outline.getExample(executionPoint.exampleLine)
            if (example != null) {
                val exBp = example.findBreakpoint()
                if (exBp == null || !exBp.isEnabled) {
                    session.resume()
                    return
                }
            }
        }

        highlightExecutionPosition(step)
    }

    private fun breakpointAt(pos: com.intellij.xdebugger.XSourcePosition): com.intellij.xdebugger.breakpoints.XBreakpoint<*>? =
        com.intellij.xdebugger.XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints.firstOrNull {
            val sp = it.sourcePosition
            sp != null && sp.file == pos.file && sp.line == pos.line
        }

    /** True when [pos] is the step-def body line Cucumber+ would sync from [step]. */
    private fun isSyncedStepDefPosition(step: GherkinStep, pos: com.intellij.xdebugger.XSourcePosition): Boolean {
        val defs = step.findCucumberStepDefinitions()
        if (defs.isEmpty()) return false
        val best = io.nimbly.tzatziki.Tzatziki().extensionList
            .firstNotNullOfOrNull { it.findBestPositionToAddBreakpoint(defs) } ?: return false
        val bestFile = best.first.containingFile?.originalFile?.virtualFile
        return bestFile == pos.file && best.second == pos.line
    }

    override fun processStopped(debugProcess: XDebugProcess) {

        if (!TOGGLE_CUCUMBER_PL)
            return

        LOG.info("C+ XDebuggerManager.TOPIC - processStopped : " + debugProcess::class.java)
        project.cucumberExecutionTracker().removeHighlighters()
    }

    private fun highlightExecutionPosition(step: GherkinStep) {

        val project = step.project

        FileEditorManager.getInstance(project).getAllEditors(step.containingFile.virtualFile)
            .filterIsInstance<TextEditor>()
            .forEach { editor ->

                LOG.debug("C+ XDebuggerManager.TOPIC - Highlighting step")
                val doc = step.getDocument() ?: return@forEach

                val line = doc.getLineNumber(step.textOffset)
                val start = doc.getLineStartOffset(line)
                val end = doc.getLineEndOffset(line)

                val tracker = project.cucumberExecutionTracker()
                tracker.removeHighlighters()

                tracker.highlightersModel = editor.editor.markupModel
                tracker.highlighters += editor.editor.markupModel.addRangeHighlighter(
                    BREAKPOINT_STEP,
                    start, end,
                    DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                var offsetToScrollTo = step.textOffset

                val exampleLine = tracker.exampleLine
                if (step.stepHolder is GherkinScenarioOutline) {

                    val row = (step.stepHolder as GherkinScenarioOutline).getExample(exampleLine)
                    if (row != null) {

                        LOG.debug("C+ XDebuggerManager.TOPIC - Highlighting row")
                        val exline = doc.getLineNumber(row.textOffset)
                        tracker.highlighters += editor.editor.markupModel.addRangeHighlighter(
                            BREAKPOINT_EXAMPLE,
                            doc.getLineStartOffset(exline),
                            doc.getLineEndOffset(exline),
                            DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                            HighlighterTargetArea.LINES_IN_RANGE
                        )
                        offsetToScrollTo = row.textOffset
                    }
                }

                val vp = editor.editor.offsetToLogicalPosition(offsetToScrollTo)
                editor.editor.scrollingModel.scrollTo(vp, ScrollType.MAKE_VISIBLE)
            }
    }
}