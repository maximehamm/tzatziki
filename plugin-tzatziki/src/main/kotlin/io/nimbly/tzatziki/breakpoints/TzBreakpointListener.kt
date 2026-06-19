package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import java.util.Collections
import java.util.WeakHashMap
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl

// Cucumber+ uses a dedicated [TzCucumberCodeBreakpointType] to identify the code-side
// breakpoints it creates (see #cucumber-scope feature branch). The previous
// {@code "Cucumber+"!=null} fake-condition trick has been removed.
@Deprecated("Replaced by isCucumberSyncBreakpoint() — kept as compile-time stub for any external reference.")
const val CUCUMBER_FAKE_EXPRESSION = "\"Cucumber+\"!=null"

class TzBreakpointListener(private val project: Project) : XBreakpointListener<XBreakpoint<*>> {

    private val LOG = Logger.getInstance(TzBreakpointListener::class.java)

    @Volatile private var addInProgress = false
    @Volatile private var removeInProgress = false

    // Per-breakpoint signature cache (enabled flag + condition text + suspend policy).
    // Issue #124-bis: `breakpointChanged` fires on every document edit that shifts a
    // breakpoint's line — IntelliJ recomputes line positions and re-fires the event
    // even though nothing semantically changed. The previous global `changeInProgress`
    // flag only prevented overlapping work; it still ran a full project-wide
    // `ReferencesSearch` (→ `TzCucumberStepReference.multiResolve` on every potential
    // match) on each keystroke, freezing autocompletion on large projects + WSL.
    // We now compare an actionable signature and bail when nothing relevant changed.
    private val lastSignatures: MutableMap<XBreakpoint<*>, String> =
        Collections.synchronizedMap(WeakHashMap())

    // Debounce: coalesce rapid-fire CHANGED events for the same breakpoint into a
    // single refresh ~150ms after the last event. Backed by a project-scoped Alarm
    // (POOLED_THREAD) so it disposes cleanly when the project closes.
    //
    // Why only 150ms: `signatureOf` is position-independent, so keystroke-driven line
    // drifts bail at the early-return above and never reach this debounce. The only
    // events left to coalesce are genuine semantic changes (enable/condition/suspend/
    // log) — which never arrive per-keystroke. A long delay only added perceived lag
    // when muting a single breakpoint (the paired Gherkin↔code BP took ~1s to mirror).
    // 150ms still collapses a "mute all" burst into one pass.
    private val debounce = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val pendingChange: MutableSet<XBreakpoint<*>> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    // Positions (fileUrl, line) of code breakpoints we are about to create as the synced
    // counterpart of a *Gherkin* breakpoint. `ensureCucumberCodeBreakpoint` defers the
    // actual creation to `invokeLater`, so by the time `refreshCode`'s ADDED handler sees
    // the new code BP, `addInProgress` has already been reset and can no longer suppress
    // it. We use this set to recognise "this code BP was born from a Gherkin step" and
    // skip the back-propagation that would otherwise create Gherkin BPs on the *sibling*
    // steps sharing the same step definition. Code BPs placed directly by the user are
    // NOT in this set and still propagate to all mapped steps (the desired behaviour).
    private val pendingGherkinSync: MutableSet<Pair<String, Int>> =
        Collections.synchronizedSet(HashSet())

    // Positions (fileUrl, line) of code breakpoints whose enabled state we are about to
    // flip from the Gherkin side (a step was muted/unmuted, changing whether ANY linked
    // step is still active). refreshCode's CHANGED handler consumes this marker to avoid
    // back-propagating the flip onto the sibling Gherkin steps (which would mute/un-mute
    // them too). Direct code-side mute/unmute is NOT in this set and still propagates.
    private val pendingGherkinStateChange: MutableSet<Pair<String, Int>> =
        Collections.synchronizedSet(HashSet())

    private fun signatureOf(b: XBreakpoint<*>): String {
        // Position-independent — line shifts must NOT invalidate this.
        val cond = runCatching { b.conditionExpression?.expression }.getOrNull().orEmpty()
        val log  = runCatching { b.logExpressionObject?.expression }.getOrNull().orEmpty()
        val susp = runCatching { b.suspendPolicy?.name }.getOrNull().orEmpty()
        return "${b.isEnabled}|$susp|$cond|$log"
    }

    override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
        if (!TOGGLE_CUCUMBER_PL || !io.nimbly.tzatziki.config.TzSettings.getInstance().isBreakpointSyncEnabled() || !isJavaPresent()) return

        // Skip events where only the line position drifted — those re-fire on every
        // keystroke and don't require any Cucumber+ resync work.
        val sig = signatureOf(breakpoint)
        val old = lastSignatures.put(breakpoint, sig)
        if (old == sig) return

        // Coalesce: cancel any pending refresh for this same breakpoint, then schedule a
        // fresh one. Multiple CHANGED events arriving within 600ms collapse into one.
        pendingChange.add(breakpoint)
        debounce.cancelAllRequests()
        debounce.addRequest({
            val toProcess = synchronized(pendingChange) {
                val snap = pendingChange.toList()
                pendingChange.clear()
                snap
            }
            toProcess.forEach { bp ->
                runRefresh(bp, EAction.CHANGED) { /* no flag */ }
            }
        }, 150)
    }

    override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
        if (!TOGGLE_CUCUMBER_PL || !io.nimbly.tzatziki.config.TzSettings.getInstance().isBreakpointSyncEnabled() || !isJavaPresent()) return
        if (addInProgress) return
        addInProgress = true
        lastSignatures[breakpoint] = signatureOf(breakpoint)
        scheduleRefresh(breakpoint, EAction.ADDED) { addInProgress = false }
    }

    override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
        if (!TOGGLE_CUCUMBER_PL || !io.nimbly.tzatziki.config.TzSettings.getInstance().isBreakpointSyncEnabled() || !isJavaPresent()) return
        if (removeInProgress) return
        removeInProgress = true
        lastSignatures.remove(breakpoint)
        scheduleRefresh(breakpoint, EAction.REMOVED) { removeInProgress = false }
    }

    /**
     * Three-phase scheduling to avoid SlowOperations on EDT in 2025.3+:
     *  1. Wait for smart mode (smartInvokeLater)
     *  2. Run heavy PSI work (step resolution, find usages) on a pooled thread inside runReadAction
     *  3. Apply the breakpoint write on EDT
     */
    private fun scheduleRefresh(breakpoint: XBreakpoint<*>, action: EAction, releaseFlag: () -> Unit) {
        DumbService.getInstance(project).smartInvokeLater {
            runRefresh(breakpoint, action, releaseFlag)
        }
    }

    private fun runRefresh(breakpoint: XBreakpoint<*>, action: EAction, releaseFlag: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // #124: the refresh does an expensive project-wide step search
                // (findStepUsages → ReferencesSearch). A plain blocking ReadAction.run
                // CANNOT be cancelled by a pending write, so it starves the EDT's
                // write-intent acquisition → UI freeze. A non-blocking read action is
                // cancellable: when a write is requested it aborts and retries, letting
                // the EDT proceed. `refresh` is read-only (it only *schedules* the
                // breakpoint writes via invokeLater), so re-running it is safe.
                ReadAction.nonBlocking<Unit> {
                    refresh(breakpoint, action)
                }.executeSynchronously()
            } catch (t: Throwable) {
                if (t !is com.intellij.openapi.progress.ProcessCanceledException)
                    LOG.warn("Cucumber+ refresh failed", t)
            } finally {
                releaseFlag()
            }
        }
    }

    override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) = Unit

    private fun refresh(breakpoint: XBreakpoint<*>, action: EAction) {
        try {
            val sp = breakpoint.sourcePosition
            val ft = sp?.file?.fileType
            LOG.info("C+ refresh: action=$action type=${breakpoint.type?.id} file=${sp?.file?.path} line=${sp?.line} fileType=$ft")
            if (ft == GherkinFileType.INSTANCE) {
                refreshGherkin(breakpoint, action)
            } else {
                refreshCode(breakpoint, action)
            }

            // Per-instance "partial mute" icon on shared step-def code breakpoints.
            // recompute() is read-action-safe (we're inside one); apply() must run on EDT.
            // Cheap: only iterates Cucumber+ code BPs and findSteps is cached.
            val partial = TzPartialMutePresentation.recompute(project)
            ApplicationManager.getApplication().invokeLater { TzPartialMutePresentation.apply(project, partial) }
        } catch (e: Throwable) {
            if (e !is com.intellij.openapi.progress.ProcessCanceledException)
                LOG.warn("Refresh issue", e)
        }
    }

    private fun refreshGherkin(gherkinBreakpoint: XBreakpoint<*>, action: EAction) {

        val vfile = gherkinBreakpoint.sourcePosition?.file ?: return
        val line = gherkinBreakpoint.sourcePosition?.line ?: return

        val file = vfile.getFile(project) ?: return
        val doc = file.getDocument() ?: return
        val lineRange = doc.getLineRange(line).shrink(1, 1)

        val step = file.findElementsOfTypeInRange(lineRange, GherkinStep::class.java).firstOrNull()
        if (step != null && line == step.getDocumentLine())
            refreshGherkinStep(step, gherkinBreakpoint, action)

        val row = file.findElementsOfTypeInRange(lineRange, GherkinTableRow::class.java).firstOrNull()
        if (row != null && row !is GherkinTableHeaderRowImpl)
            refreshGherkinRow(row, gherkinBreakpoint, action)
    }

    private fun refreshGherkinRow(row: GherkinTableRow, gherkinBreakpoint: XBreakpoint<*>, action: EAction) {

        val examples = row.parentOfTypeIs<GherkinExamplesBlock>(true) ?: return
        val scenario = examples.parentOfTypeIs<GherkinScenarioOutline>(true) ?: return

        if (action == EAction.ADDED) {

            if (scenario.steps.find { it.findBreakpoint() != null } != null) return

            scenario.steps.forEach { step ->
                val documentLine = step.getDocumentLine() ?: return@forEach
                step.toggleGherkinBreakpoint(documentLine)
                addInProgress = false // Let recurse !
                refreshGherkinStep(step, null, EAction.ADDED)
            }
        }
        else if (action == EAction.REMOVED) {
            val hasStillRowBreakpoint = scenario.allExamples().find { it.findBreakpoint() != null } != null
            if (!hasStillRowBreakpoint) {
                scenario.steps.forEach { it.deleteBreakpoints() }
            }
        }
        else if (action == EAction.CHANGED) {
            val state = gherkinBreakpoint.isEnabled
            if (gherkinBreakpoint.isEnabled ||
                scenario.allExamples().filter { it != row }.find { it.findBreakpoint() != null && it.findBreakpoint()?.isEnabled != gherkinBreakpoint.isEnabled } == null) {

                scenario.steps.forEach {
                    it.enableBreakpoints(state)
                }
            }
        }
    }

    private fun refreshGherkinStep(step: GherkinStep, gherkinBreakpoint: XBreakpoint<*>?, action: EAction) {

        val stepDefinitions = step.findCucumberStepDefinitions()
        LOG.info("C+ refreshGherkinStep: step='${step.text}' stepDefs=${stepDefinitions.size} action=$action")
        if (stepDefinitions.isEmpty()) {
            LOG.info("C+ refreshGherkinStep: no step defs found — bailing (Java BP won't be created)")
            return
        }

        val codeElement = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findBestPositionToAddBreakpoint(stepDefinitions)
        }
        if (codeElement == null) {
            LOG.info("C+ refreshGherkinStep: no best position found across ${Tzatziki().extensionList.size} extensions — bailing")
            return
        }
        LOG.info("C+ refreshGherkinStep: best position = ${codeElement.first.containingFile?.virtualFile?.path}:${codeElement.second}")

        // #124 perf: avoid the project-wide reverse search on the hot path.
        // For ADDED/CHANGED we only need the code-side breakpoints sitting INSIDE the
        // resolved step-def element — a direct breakpoint-manager query, no PSI search.
        // The reverse "which Gherkin steps map to this def" lookup is expensive and is
        // done lazily, only on REMOVED (below).
        val codeVfile = codeElement.first.containingFile?.originalFile?.virtualFile
        val codeRange = codeElement.first.textRange
        val codeLine = codeElement.second
        // Offset that reliably resolves back to the step definition via the extension's
        // findStepsAndBreakpoints. It MUST be the step-def element's own start offset, NOT
        // the line-start (column 0) offset: for JS/TS, findElementAt(column 0) lands on
        // indentation whitespace and does not walk up to the enclosing cucumber call, so the
        // reverse findSteps lookup would return an empty list.
        val codeOffset = codeRange?.startOffset
        // Identify the synced code breakpoint(s) by LINE — NOT by codeRange.contains(offset).
        // Line breakpoints carry the line as their identity, and in JS/TS the breakpoint's
        // source offset sits at the line start (indentation), BEFORE the resolved statement's
        // textRange — so a textRange test wrongly returned an empty list and broke removal /
        // mute mirroring on the code side.
        val codeBreakpoints: List<XBreakpoint<*>> =
            if (codeVfile == null) emptyList()
            else XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints.filter {
                val sp = it.sourcePosition
                sp != null && sp.file == codeVfile && sp.line == codeLine
            }

        if (action == EAction.ADDED) {
            if (codeBreakpoints.none { it.isCucumberSyncBreakpoint() }) {
                // Mark the code BP we're about to create as "synced from Gherkin" so that
                // refreshCode's ADDED handler does NOT back-propagate Gherkin BPs onto the
                // sibling steps that share this same step definition (regression: a shared
                // impl made every other step sprout a breakpoint).
                codeElement.first.containingFile?.virtualFile?.let { f ->
                    pendingGherkinSync.add(f.url to codeElement.second)
                }
                ensureCucumberCodeBreakpoint(codeElement, project)
            }

            val scenario = step.parentOfTypeIs<GherkinScenarioOutline>(true)
            if (scenario != null) {
                val examples = scenario.allExamples()
                val hasBreakpoints = examples.find { it.findBreakpoint() != null } != null
                if (!hasBreakpoints) {
                    examples.forEach {
                        val documentLine = it.getDocumentLine() ?: return@forEach
                        it.toggleGherkinBreakpoint(documentLine)
                    }
                }
            }
        }
        else if (action == EAction.REMOVED) {
            // Reverse lookup needed ONLY here: are there other Gherkin steps (still
            // breakpointed) mapping to this same code def? If none, drop the synced
            // code breakpoint(s).
            val steps = Tzatziki.findSteps(codeVfile, codeOffset)
            val stepBreakpoints = steps.mapNotNull { it.findBreakpoint() }.size

            if (stepBreakpoints == 0) {
                codeBreakpoints.forEach { b ->
                    XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
                }
            }

            val scenarioStillHasBreakpoints = step.stepHolder.steps.find { it.findBreakpoint() != null } != null
            if (step.stepHolder is GherkinScenarioOutline && !scenarioStillHasBreakpoints) {
                val allExamples = (step.stepHolder as GherkinScenarioOutline).allExamples()
                allExamples.forEach { it.deleteBreakpoints() }
            }
        }
        else if (action == EAction.CHANGED && gherkinBreakpoint != null) {
            val state = gherkinBreakpoint.isEnabled

            // Gherkin → code mute/unmute. The shared code breakpoint must be ENABLED as
            // soon as ANY linked Gherkin step is still active (so the debugger can stop for
            // it), and DISABLED only once ALL linked steps are muted. This keeps per-step
            // independence when several steps map to the same definition: muting one among
            // others leaves the code BP enabled (mixed → shown via the partial ring icon),
            // muting them all flips the code BP to disabled (native "hollow" icon). The
            // code → Gherkin back-propagation of this flip is suppressed (pendingGherkin
            // StateChange) so the sibling steps are not toggled in turn.
            val mappedStates = Tzatziki.findSteps(codeVfile, codeOffset).mapNotNull { it.findBreakpoint()?.isEnabled }
            if (mappedStates.isNotEmpty()) {
                val anyEnabled = mappedStates.any { it }
                codeBreakpoints.forEach { cb ->
                    if (cb.isEnabled != anyEnabled) {
                        cb.sourcePosition?.let { sp -> pendingGherkinStateChange.add(sp.file.url to sp.line) }
                        cb.isEnabled = anyEnabled
                    }
                }
            }

            val scenario = step.parentOfTypeIs<GherkinScenarioOutline>(true)
            if (scenario != null) {
                if (gherkinBreakpoint.isEnabled ||
                    scenario.steps.filter { it != step }.find { it.findBreakpoint() != null && it.findBreakpoint()?.isEnabled != gherkinBreakpoint.isEnabled } == null) {

                    scenario.allExamples().forEach {
                        it.enableBreakpoints(state)
                    }
                }
            }
        }
    }

    private fun refreshCode(breakpoint: XBreakpoint<*>, action: EAction) {

        // Cucumber+ only deals with LINE breakpoints. Method/field/exception breakpoints are
        // out of scope — leave them alone to avoid promoting/syncing the wrong type.
        if (breakpoint !is com.intellij.xdebugger.breakpoints.XLineBreakpoint<*>) return

        val pair = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findStepsAndBreakpoints(
                breakpoint.sourcePosition?.file,
                breakpoint.sourcePosition?.offset)
        } ?: return

        val steps = pair.first
        val codeBreakpoints = pair.second
        val isAlreadyOurType = breakpoint.isCucumberSyncBreakpoint()

        // Two-language promotion paths:
        //  - JVM files (.java / .kt / .scala): use BreakpointsUtil.promoteToCucumberType
        //    which creates a `tzatziki.cucumber.code` BP (JavaLineBreakpointType subclass).
        //  - JS / TS files: delegate to the extension's own promoteToCucumberType so
        //    each language can create its language-specific Cucumber+ type
        //    (e.g. JS → `tzatziki.cucumber.code.javascript` using JavaScriptLineBreakpointProperties).
        val ext = breakpoint.sourcePosition?.file?.extension?.lowercase()
        val isJvmLanguage = ext == "java" || ext == "kt" || ext == "kts" || ext == "scala"

        if (action == EAction.ADDED && steps.isNotEmpty() && !isAlreadyOurType) {
            // Only promote when the user's breakpoint sits at the EXACT body line that
            // Cucumber+ would itself sync from a Gherkin step. Clicks on the method
            // declaration line, on a Javadoc line, or anywhere else inside the method
            // body remain plain language breakpoints (they keep the standard gutter icon).
            val stepDefs = steps.flatMap { it.findCucumberStepDefinitions() }
            val bestLine = Tzatziki().extensionList
                .firstNotNullOfOrNull { it.findBestPositionToAddBreakpoint(stepDefs) }
                ?.second
            if (bestLine == null || breakpoint.line != bestLine) {
                // Clicks on the declaration line, a doc line, or anywhere other than the
                // synced body line stay plain language breakpoints — nothing to do.
                return
            }
            val promoted = if (isJvmLanguage) {
                promoteToCucumberType(breakpoint, project)
                true
            } else {
                // Let any matching extension handle the non-JVM promotion.
                Tzatziki().extensionList.any { it.promoteToCucumberType(breakpoint, project) }
            }
            // When promoted, a fresh ADDED event fires for the now-Cucumber+ breakpoint and
            // drives the Gherkin propagation below. When NOT promoted — e.g. Python keeps the
            // native `python-line` type — there is no follow-up event, so we must fall through
            // and propagate to the linked Gherkin steps now, treating this native breakpoint
            // as the synced code breakpoint.
            if (promoted) return
        }

        // Was this code BP just created as the synced counterpart of a Gherkin step?
        // If so, we must NOT back-propagate Gherkin breakpoints onto the sibling steps
        // that share the same step definition — only the step the user actually clicked
        // already has its Gherkin BP. Consume the marker (it's a one-shot).
        val fromGherkinSync = run {
            val url = breakpoint.sourcePosition?.file?.url
            url != null && pendingGherkinSync.remove(url to breakpoint.line)
        }

        // Same idea for mute/unmute: when refreshGherkinStep just flipped this code BP's
        // enabled state (because all / no longer all of its linked steps are muted), do NOT
        // mirror that flip back onto the sibling Gherkin steps — that would mute/un-mute
        // them too. A direct code-side mute is NOT marked and still propagates to all steps.
        val fromGherkinChange = run {
            val url = breakpoint.sourcePosition?.file?.url
            url != null && pendingGherkinStateChange.remove(url to breakpoint.line)
        }

        steps.forEach { step ->
            val documentLine = step.getDocumentLine() ?: return@forEach

            if (action == EAction.ADDED) {
                // Always make sure each linked step has a Gherkin breakpoint. Whether the
                // event is the original user click (now our type after promotion) or the
                // result of a Gherkin → code sync, we only ADD when there is none yet.
                // Exception: when the code BP itself was born from a Gherkin step
                // (fromGherkinSync), creating Gherkin BPs here would wrongly mirror it onto
                // every sibling step sharing the impl — so we skip the creation.
                if (!fromGherkinSync) {
                    val oldStepBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
                        .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                        .filter { it.sourcePosition?.line == step.getDocumentLine() }

                    if (oldStepBreakpoints.isEmpty()) {
                        step.toggleGherkinBreakpoint(documentLine)
                    }
                }
                step.updatePresentation(codeBreakpoints)
            }
            else if (action == EAction.REMOVED && codeBreakpoints.isEmpty()) {
                step.deleteBreakpoints()
            }
            else if (action == EAction.CHANGED && breakpoint is com.intellij.xdebugger.breakpoints.XLineBreakpoint<*>) {
                // Mirror the code-side enabled state onto the paired Gherkin
                // breakpoint(s). Without this, muting a JS / TS / Java BP leaves
                // its Gherkin counterpart enabled (and vice-versa is already
                // handled by refreshGherkinStep's CHANGED branch).
                // Skipped when the flip originated from the Gherkin side (fromGherkinChange):
                // mirroring it back would mute/un-mute the sibling steps.
                // updatePresentation() forces every linked step's Gherkin BP enabled-state to
                // match the code BP — so it is itself a code → Gherkin propagation and MUST be
                // skipped too when the flip came from the Gherkin side, otherwise un-muting one
                // step (which re-enables the shared code BP) would un-mute the siblings.
                if (!fromGherkinChange) {
                    val state = breakpoint.isEnabled
                    XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
                        .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                        .filter { it.sourcePosition?.line == step.getDocumentLine() }
                        .forEach { gbp ->
                            if (gbp.isEnabled != state) gbp.isEnabled = state
                        }
                    step.updatePresentation(codeBreakpoints)
                }
            }
            else {
                step.updatePresentation(codeBreakpoints)
            }
        }
    }

    private fun GherkinPsiElement.enableBreakpoints(enabled: Boolean) {
        val oldBreakpoints = XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
            .filter { it.sourcePosition?.file == containingFile.virtualFile }
            .filter { it.sourcePosition?.line == getDocumentLine() }
        oldBreakpoints.forEach { b ->
            b.isEnabled = enabled
        }
    }
}

enum class EAction { CHANGED, ADDED, REMOVED }
