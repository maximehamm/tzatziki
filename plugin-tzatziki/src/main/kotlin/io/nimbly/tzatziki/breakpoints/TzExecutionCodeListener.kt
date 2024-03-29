package io.nimbly.tzatziki.breakpoints

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession.Event
import com.intellij.execution.RunManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.ui.DebuggerColors
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.findBreakpoint
import io.nimbly.tzatziki.util.findStep
import io.nimbly.tzatziki.util.getDocument
import io.nimbly.tzatziki.util.getExample
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzExecutionCodeListener : StartupActivity {

    private val LOG = logger<TzExecutionCodeListener>()

    override fun runActivity(project: Project) {

        project.messageBus
            .connect()
            .subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {

                override fun processStarted(debugProcess: XDebugProcess) {

                    LOG.info("C+ XDebuggerManager.TOPIC - processStarted : " + debugProcess::class.java)
                    if (debugProcess !is JavaDebugProcess) return

                    if (!TOGGLE_CUCUMBER_PL)
                        return

                    debugProcess.debuggerSession.contextManager.addListener(object : DebuggerContextListener {

                        override fun changeEvent(newContext: DebuggerContextImpl, event: Event?) {

                            LOG.info("C+ XDebuggerManager.TOPIC - changeEvent = $event")
                            if (event != Event.PAUSE)
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

                            // Check that breakpoint is marked as managed by C+
                            val codeBreakpoint = newContext.sourcePosition.elementAt.findBreakpoint()
                            if (codeBreakpoint?.conditionExpression?.expression != CUCUMBER_FAKE_EXPRESSION) {

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

                override fun processStopped(debugProcess: XDebugProcess) {

                    LOG.info("C+ XDebuggerManager.TOPIC - processStopped : " + debugProcess::class.java)
                    project.cucumberExecutionTracker().removeHighlighters()
                }
            })
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
                    DebuggerColors.EXECUTIONPOINT_ATTRIBUTES,
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
                            DebuggerColors.EXECUTIONPOINT_ATTRIBUTES,
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

private fun String.noSlash(): String {
    if (this.startsWith("/"))
        return this.substringAfter("/")
    else
        return this
}
