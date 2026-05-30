/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.run

import com.intellij.execution.RunManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.findBreakpoint
import io.nimbly.tzatziki.util.findCucumberStepDefinitions
import io.nimbly.tzatziki.util.findStep
import io.nimbly.tzatziki.util.getExample
import io.nimbly.tzatziki.util.isCucumberSyncBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline

/**
 * JS / TS counterpart of [io.nimbly.tzatziki.run.TzRunCodeListener] — filters
 * Node / Chrome debug-session pauses so the debugger only stops on the **single
 * example row** the user has flagged in the .feature file, instead of breaking
 * on every iteration of a Scenario Outline.
 *
 * Decision flow (mirrors the JVM listener):
 *
 *  1. Bail if the paused process isn't a JS / Node debug session (FQN match).
 *  2. Bail if the active run config isn't a Cucumber.js one.
 *  3. Read the current step / example from [cucumberExecutionTracker] (populated
 *     by [TzExecutionCucumberListener] from the cucumber-js stdout TeamCity events).
 *  4. If the BP we're paused on isn't a Cucumber+ sync BP, do nothing — the user
 *     dropped a plain JS BP somewhere else and we shouldn't interfere.
 *  5. Otherwise:
 *      - no enabled BP on the Gherkin step → `resume()`
 *      - Scenario Outline + the current example row has no enabled BP → `resume()`
 *
 * We hook via the generic [XDebugSessionListener.sessionPaused] callback so we
 * don't need a hard dependency on the JavaScript-debugger plugin internals; the
 * process-class FQN test in [processStarted] is the only place we touch Node-
 * specific identifiers, and even there we match by string prefix to stay loose.
 */
class TzRunNodeListener(private val project: Project) : XDebuggerManagerListener {

    private val LOG = logger<TzRunNodeListener>()

    override fun processStarted(debugProcess: XDebugProcess) {
        if (!TOGGLE_CUCUMBER_PL) return

        // Detect the JS / Node debug process by FQN — avoid a hard dependency on
        // the JavaScriptDebugger plugin types. Covers `com.jetbrains.nodeJs.NodeChromeDebugProcess`
        // and any future renaming as long as the package or class name keeps the
        // `node` / `javascript.debugger` hint.
        val cls = debugProcess::class.java.name
        val isJsDebug = cls.startsWith("com.jetbrains.nodeJs.")
            || cls.contains("NodeChromeDebugProcess")
            || cls.contains("javascript.debugger")
        if (!isJsDebug) return

        val session = debugProcess.session
        LOG.info("C+ TzRunNodeListener processStarted: $cls, session=${session.javaClass.name}, suspended=${session.isSuspended}")

        // Alarm that fires the deferred filter decision on the pooled thread,
        // re-armed on every sessionPaused so successive pauses don't pile up
        // background threads sleeping concurrently (#12 perf TODO).
        val pauseAlarm = com.intellij.util.Alarm(
            com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD,
            session.project,
        )

        session.addSessionListener(object : XDebugSessionListener {
            override fun sessionPaused() {
                // Race-condition fix: cucumber-js emits its SMTRunner step events
                // 1-3 ms AFTER the JS code reaches our BP. Defer the decision
                // via an Alarm so the tracker has time to catch up — cancelling
                // any previous still-armed request first to keep at most one
                // pending check at any time.
                LOG.info("C+ TzRunNodeListener: sessionPaused fired — scheduling delayed check")
                pauseAlarm.cancelAllRequests()
                pauseAlarm.addRequest({ if (session.isSuspended) handlePause(session) }, 250)
            }
            override fun sessionResumed() {
                LOG.info("C+ TzRunNodeListener: sessionResumed fired")
            }
            override fun sessionStopped() {
                LOG.info("C+ TzRunNodeListener: sessionStopped fired")
            }
            override fun stackFrameChanged() {
                LOG.info("C+ TzRunNodeListener: stackFrameChanged fired (suspended=${session.isSuspended})")
            }
            override fun beforeSessionResume() {
                LOG.info("C+ TzRunNodeListener: beforeSessionResume fired")
            }
        })
    }

    private fun handlePause(session: com.intellij.xdebugger.XDebugSession) {
        if (!TOGGLE_CUCUMBER_PL) return

        val selected = RunManager.getInstance(project).selectedConfiguration
        val displayName = selected?.type?.displayName
        val typeId = selected?.type?.id
        LOG.info("C+ TzRunNodeListener sessionPaused: run config type id='$typeId' displayName='$displayName'")
        if (typeId != "cucumber.js" && displayName != "Cucumber.js") {
            LOG.info("C+ TzRunNodeListener sessionPaused: ignoring (not a Cucumber.js run config)")
            return
        }

        val executionPoint = project.cucumberExecutionTracker()
        LOG.info("C+ TzRunNodeListener sessionPaused: tracker featurePath='${executionPoint.featurePath}' line=${executionPoint.lineNumber} exampleLine=${executionPoint.exampleLine}")
        if (executionPoint.featurePath == null) {
            LOG.info("C+ TzRunNodeListener sessionPaused: no featurePath, ignoring")
            return
        }
        val featureVfile = executionPoint.findFile() ?: run {
            LOG.info("C+ TzRunNodeListener sessionPaused: findFile null for '${executionPoint.featurePath}'")
            return
        }
        val lineNumber = executionPoint.lineNumber ?: return
        val sp = session.currentPosition ?: return
        val exampleLine = executionPoint.exampleLine

        // Run the entire PSI-walk + BP-lookup in ONE read action — accessing
        // `step.text`, `step.stepHolder`, `getDocumentLine()` etc. outside a
        // read action throws ThreadingAssertions on 2025.3+.
        //
        // [barHeaderLine0] / [barEndLine0] (0-based) carry the progression-bar
        // span to paint when we KEEP the pause — computed from the actually
        // resolved Gherkin step, NOT from the SMTRunner tracker (whose
        // last-recorded line lags the real debugger pause, which made the bar
        // land on the wrong line during debug).
        data class Decision(
            val shouldResume: Boolean,
            val reason: String,
            val barHeaderLine0: Int? = null,
            val barEndLine0: Int? = null,
            val barIsExample: Boolean = false,
        )
        val decision: Decision = com.intellij.openapi.application.ReadAction.compute<Decision, RuntimeException> {
            // Cucumber-js reports ALL events of an outline iteration at the DATA
            // ROW line (not the step line), so `tracker.lineNumber` stays stuck on
            // whatever Step:line was last reported BEFORE the outline started — a
            // step from the previous scenario. Whenever `exampleLine != null` we
            // therefore IGNORE `lineNumber` and resolve the running step by:
            // walking PSI at `exampleLine` → up to the enclosing outline → iterate
            // its steps and pick the one whose step-def matches the JavaScript
            // breakpoint we're paused on (sp.file:sp.line).
            val step = if (exampleLine == null) {
                findStep(featureVfile, project, lineNumber)
                    ?: return@compute Decision(false, "no step at ${featureVfile.path}:$lineNumber — keeping pause as-is")
            } else {
                val psiFile = PsiManager.getInstance(project).findFile(featureVfile)
                val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(featureVfile)
                if (psiFile == null || doc == null) {
                    return@compute Decision(false, "no PSI / doc for ${featureVfile.path} — keeping pause")
                }
                val rowLineIdx = (exampleLine - 1).coerceIn(0, doc.lineCount - 1)
                val rowOffset = doc.getLineStartOffset(rowLineIdx)
                val rowElement = psiFile.findElementAt(rowOffset)
                val outline = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    rowElement,
                    org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline::class.java,
                ) ?: return@compute Decision(false, "exampleLine=$exampleLine not inside an outline — keeping pause")
                // Find which outline step has a step-def whose enclosing
                // `Given(...) / When(...) / Then(...)` call EXPRESSION contains
                // the JS pause position. The step-def `element` from the
                // cucumber-js plugin is the regex JSLiteralExpression — we walk
                // up to its enclosing JSCallExpression so its textRange covers
                // the whole callback body (which is where our BPs sit).
                val match = outline.steps.firstOrNull { s ->
                    s.findCucumberStepDefinitions().any { d ->
                        val el = d.element ?: return@any false
                        if (el.containingFile?.virtualFile != sp.file) return@any false
                        val enclosingCall = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                            el, com.intellij.lang.javascript.psi.JSCallExpression::class.java, false,
                        ) ?: return@any false
                        enclosingCall.textRange.contains(sp.offset)
                    }
                }
                match ?: return@compute Decision(false, "outline at exampleLine=$exampleLine has no step whose step-def callExpr covers sp.file=${sp.file.name}:${sp.line} (offset=${sp.offset}) — keeping pause")
            }

            // Strategy: walk the project's XBreakpointManager and find any Cucumber+
            // code BP whose sourcePosition matches the current pause location
            // (same file URL + same line). This is more robust than the PSI-based
            // `findBreakpoint()` because TS source-maps can shift `sp.file` to
            // a different VirtualFile than the one we registered the BP on.
            val allBps = com.intellij.xdebugger.XDebuggerManager.getInstance(project)
                .breakpointManager.allBreakpoints
            val matchingByUrl = allBps.filter { it.sourcePosition?.file == sp.file }
            val codeBp = matchingByUrl.firstOrNull {
                it.sourcePosition?.line == sp.line && it.isCucumberSyncBreakpoint()
            }
            LOG.info("C+ TzRunNodeListener sessionPaused: sp.file=${sp.file.path} line=${sp.line}, matching-by-url=${matchingByUrl.size}, codeBp=${codeBp?.type?.id}")
            if (codeBp == null) {
                // Fall back to a PSI lookup — useful for the case where the
                // pause and the BP source positions don't share a VirtualFile
                // (different source-map endpoints).
                val psiBp = PsiManager.getInstance(project)
                    .findFile(sp.file)?.findElementAt(sp.offset)?.findBreakpoint()
                if (psiBp?.isCucumberSyncBreakpoint() != true) {
                    return@compute Decision(false, "paused on non-Cucumber+ BP (sp.file=${sp.file.name}:${sp.line}, allBps=${allBps.size}, matching=${matchingByUrl.size}, psiBp=${psiBp?.type?.id}) — keeping pause")
                }
            }

            val gherkinBp = step.findBreakpoint()
            if (gherkinBp == null || !gherkinBp.isEnabled) {
                return@compute Decision(true, "no Gherkin BP on '${step.text}' (step at line $lineNumber) — resuming")
            }

            val outline = step.stepHolder as? GherkinScenarioOutline
            if (outline != null) {
                val example = outline.getExample(exampleLine)
                if (example != null) {
                    val exampleBp = example.findBreakpoint()
                    if (exampleBp == null || !exampleBp.isEnabled) {
                        return@compute Decision(true, "example row line=$exampleLine not flagged — resuming")
                    }
                }
            }

            // Keeping the pause — compute the progression-bar span from the
            // resolved step: anchor at the enclosing scenario / outline header,
            // end at the step's own line. For an outline iteration end on the
            // example data row instead so the bar reaches the running row.
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(featureVfile)
            val headerLine0 = doc?.getLineNumber(step.stepHolder.textRange.startOffset)
            val endLine0 = if (outline != null && exampleLine != null) {
                (exampleLine - 1)
            } else {
                doc?.getLineNumber(step.textRange.startOffset)
            }
            Decision(
                shouldResume = false,
                reason = "pause matches a flagged Gherkin step / example — keeping pause",
                barHeaderLine0 = headerLine0,
                barEndLine0 = endLine0,
                barIsExample = (outline != null && exampleLine != null),
            )
        }

        LOG.info("C+ TzRunNodeListener sessionPaused: ${decision.reason}")
        if (decision.shouldResume) {
            session.resume()
        } else if (decision.barHeaderLine0 != null && decision.barEndLine0 != null) {
            // Paint the bar from the REAL pause position (overwrites any stale
            // bar the SMTRunner-driven TzNodeExecutionTrackerListener may have
            // drawn at the previous step's line).
            paintCucumberProgression(
                project = project,
                vfile = featureVfile,
                lineStart = decision.barHeaderLine0,
                lineEnd = decision.barEndLine0,
                isExample = decision.barIsExample,
            )
        }
    }
}
