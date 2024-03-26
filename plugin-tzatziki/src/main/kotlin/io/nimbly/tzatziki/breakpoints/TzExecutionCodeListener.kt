package io.nimbly.tzatziki.breakpoints

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.RunManager
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
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.breakpoints.TzExecutionCucumberListener.Companion.cucumberExecutionTracker
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline

class TzExecutionCodeListener : StartupActivity {

    override fun runActivity(project: Project) {

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
                            val executionPoint = project.cucumberExecutionTracker()
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

                            // Check if line breakpoint existe
                            val scenarioOutline = step.stepHolder as? GherkinScenarioOutline
                            if (scenarioOutline != null) {

                                val example = scenarioOutline.getExample(executionPoint.exampleNumber)
                                if (example != null) {

                                    val exampleBreakpoint = example.findBreakpoint()
                                    if (exampleBreakpoint == null || !exampleBreakpoint.isEnabled) {
                                        debugProcess.debuggerSession.xDebugSession?.resume()
                                        return
                                    }
                                }
                            }

                            // Highlight ghekin step at breakpoint (and also examples if)
                            FileEditorManager.getInstance(project).getAllEditors(step.containingFile.virtualFile)
                                .filterIsInstance<TextEditor>()
                                .forEach { editor ->

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

                                    val exampleNumber = tracker.exampleNumber
                                    if (step.stepHolder is GherkinScenarioOutline) {

                                        val row = (step.stepHolder as GherkinScenarioOutline).getExample(exampleNumber ?: 0)
                                        if (row != null) {
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
                    })
                }

                override fun processStopped(debugProcess: XDebugProcess) {
                    project.cucumberExecutionTracker().removeHighlighters()
                }
            })
    }
}