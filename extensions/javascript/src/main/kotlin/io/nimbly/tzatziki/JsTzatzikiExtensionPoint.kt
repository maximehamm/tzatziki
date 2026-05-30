/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.javascript.debugger.breakpoints.JavaScriptLineBreakpointProperties
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.psi.util.PsiModificationTracker
import io.nimbly.tzatziki.breakpoints.TzCucumberJsBreakpointType
import io.nimbly.tzatziki.util.findCucumberStepDefinitions
import io.nimbly.tzatziki.util.findProject
import io.nimbly.tzatziki.util.findStepUsages
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.getFile
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.plugins.cucumber.javascript.JavaScriptStepDefinition
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * JavaScript / TypeScript implementation of [TzatzikiExtensionPoint].
 *
 * Step definitions in cucumber-javascript look like a top-level call:
 *
 *   ```
 *   Given(/^I have (\d+) books$/, function (n) { /* body */ });
 *   When ("I order a {string}",    (drink: string) => { /* body */ });
 *   ```
 *
 * The cucumber-javascript IntelliJ plugin parses these into
 * [JavaScriptStepDefinition] instances whose `element` is the regex / template
 * literal passed as first argument. From there we walk up to the surrounding
 * [JSCallExpression] (the `Given(…)` call) and pick the callback ([JSFunctionExpression])
 * — that's the "method body" Cucumber+ targets for breakpoint promotion and
 * Gherkin synchronisation.
 */
class JsTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    private val log = Logger.getInstance(JsTzatzikiExtensionPoint::class.java)

    // Cache for the expensive Gherkin reverse-search (enumerates EVERY .feature
    // file in the project + walks its steps). Keyed by (step-def file URL, call
    // offset) and tagged with the project's PSI modCount so edits invalidate it.
    // Holds GherkinStep PSI elements — valid only while modCount is unchanged,
    // which is exactly the cache-hit condition. Big-project perf (#a).
    private data class StepsKey(val vfileUrl: String, val callStartOffset: Int)
    private data class StepsEntry(val modCount: Long, val steps: List<GherkinStep>)
    private val stepsCache = ConcurrentHashMap<StepsKey, StepsEntry>()

    override fun isDeprecated(element: PsiElement): Boolean {
        // Walk to the surrounding call's callback function; check JSDoc `@deprecated`
        // either on the callback itself, or on the enclosing call (e.g. someone tagged
        // the whole `Given(...)` line). Returns true on the first match found.
        val call = findEnclosingCucumberCall(element) ?: return false
        return hasDeprecatedJsDoc(call) || findCallbackOf(call)?.let { hasDeprecatedJsDoc(it) } == true
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        return stepDefinitions.any { it is JavaScriptStepDefinition }
    }

    override fun findStepsAndBreakpoints(
        vfile: VirtualFile?,
        offset: Int?,
    ): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {

        if (vfile == null || offset == null) {
            log.info("C+ JS findStepsAndBreakpoints: bail — vfile or offset null (vfile=${vfile?.name} offset=$offset)")
            return null
        }
        val ext = vfile.extension?.lowercase()
        if (ext != "js" && ext != "ts" && ext != "mjs" && ext != "cjs" && ext != "jsx" && ext != "tsx") {
            // Not a JS/TS file — let the other extensions handle it.
            return null
        }

        val project = vfile.findProject() ?: run {
            log.info("C+ JS findStepsAndBreakpoints: bail — no project for ${vfile.name}")
            return null
        }
        val file = vfile.getFile(project) ?: run {
            log.info("C+ JS findStepsAndBreakpoints: bail — getFile null for ${vfile.name}")
            return null
        }
        val element = file.findElementAt(offset) ?: run {
            log.info("C+ JS findStepsAndBreakpoints: bail — findElementAt($offset) null in ${vfile.name}")
            return null
        }

        val call = findEnclosingCucumberCall(element) ?: return null
        val callback = findCallbackOf(call) ?: return null

        val callFile = call.containingFile?.virtualFile ?: return null
        val callRange = call.textRange

        // Gherkin steps via the cached reverse-search (the expensive part).
        val steps = findGherkinStepsForCallCached(project, callFile, callRange)

        // Existing breakpoints whose source position falls inside the callback
        // body — computed fresh every call (breakpoint state changes
        // independently of PSI, so it must not be cached).
        val callbackRange = callback.textRange
        val callbackVfile = callback.containingFile?.originalFile?.virtualFile
        val allBreakpoints = XDebuggerManager.getInstance(project).breakpointManager
            .allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter {
                val sp = it.sourcePosition ?: return@filter false
                sp.file == callbackVfile && callbackRange.contains(sp.offset)
            }

        return steps to allBreakpoints
    }

    /**
     * Reverse-search: enumerate every `.feature` file in the project and collect
     * the Gherkin steps whose step-def resolves into [callRange] of [callFile].
     * Cached by (file, call offset, PSI modCount) — this is the hot path
     * triggered by both breakpoint events and every gutter-marker repaint.
     *
     * (We reverse the direction because the cucumber-javascript plugin indexes
     * its step-defs as `JSImplicitElementImpl` stubs and resolves Gherkin refs to
     * THOSE — a `ReferencesSearch` from our `JSCallExpression` returns nothing.)
     */
    private fun findGherkinStepsForCallCached(
        project: com.intellij.openapi.project.Project,
        callFile: VirtualFile,
        callRange: com.intellij.openapi.util.TextRange,
    ): List<GherkinStep> {
        val modCount = PsiModificationTracker.getInstance(project).modificationCount
        val key = StepsKey(callFile.url, callRange.startOffset)
        stepsCache[key]?.let { if (it.modCount == modCount) return it.steps }

        val steps = mutableListOf<GherkinStep>()
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        val gherkinFiles = com.intellij.psi.search.FileTypeIndex.getFiles(
            org.jetbrains.plugins.cucumber.psi.GherkinFileType.INSTANCE, scope,
        )
        for (gvfile in gherkinFiles) {
            val gpsiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(gvfile) ?: continue
            PsiTreeUtil.findChildrenOfType(gpsiFile, GherkinStep::class.java).forEach { step ->
                val matched = step.findCucumberStepDefinitions().any { d ->
                    d is JavaScriptStepDefinition &&
                        d.element?.containingFile?.virtualFile == callFile &&
                        d.element?.textOffset?.let { callRange.contains(it) } == true
                }
                if (matched) steps.add(step)
            }
        }
        stepsCache[key] = StepsEntry(modCount, steps)
        if (stepsCache.size > 512) {
            stepsCache.keys.take(stepsCache.size - 512).forEach { stepsCache.remove(it) }
        }
        return steps
    }

    /** Local copy of BreakpointsUtil.onEdtWrite (private there) so we can do
     *  add+remove of the JS breakpoint inside a write action from any thread. */
    private inline fun runOnEdtWrite(crossinline block: () -> Unit) {
        val app = com.intellij.openapi.application.ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            com.intellij.openapi.application.WriteAction.run<Throwable> { block() }
        } else {
            app.invokeLater(
                { com.intellij.openapi.application.WriteAction.run<Throwable> { block() } },
                com.intellij.openapi.application.ModalityState.nonModal(),
            )
        }
    }

    private fun dumpParents(element: PsiElement, depth: Int): String {
        val parts = mutableListOf<String>()
        var p: PsiElement? = element
        var i = 0
        while (p != null && i < depth) {
            parts += "${p.javaClass.simpleName}('${p.text.take(20).replace('\n', ' ')}')"
            p = p.parent
            i++
        }
        return parts.joinToString(" → ")
    }

    /**
     * Promotes a user-created native JavaScript line breakpoint sitting on a
     * cucumber-js step-def body line into a [TzCucumberJsBreakpointType] BP,
     * preserving the user's settings.
     */
    override fun promoteToCucumberType(breakpoint: XLineBreakpoint<*>, project: Project): Boolean {
        val vfile = breakpoint.sourcePosition?.file ?: return false
        val ext = vfile.extension?.lowercase()
        if (ext != "js" && ext != "ts" && ext != "mjs" && ext != "cjs" && ext != "jsx" && ext != "tsx") {
            return false  // Not a JS / TS file — let another extension handle it.
        }
        // Already our type? nothing to do — but we still own the file, so return true
        // to short-circuit the listener's fallback chain.
        if (breakpoint.type is TzCucumberJsBreakpointType) return true

        val line = breakpoint.line
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        val type = XDebuggerUtil.getInstance()
            .findBreakpointType(TzCucumberJsBreakpointType::class.java)
            ?: return false

        val wasEnabled = breakpoint.isEnabled
        val suspendPolicy = breakpoint.suspendPolicy
        val condition = breakpoint.conditionExpression
        val logExpr = breakpoint.logExpressionObject

        runOnEdtWrite {
            val ourBp = manager.addLineBreakpoint(type, vfile.url, line, JavaScriptLineBreakpointProperties())
            ourBp.isEnabled = wasEnabled
            ourBp.suspendPolicy = suspendPolicy
            ourBp.conditionExpression = condition
            ourBp.logExpressionObject = logExpr
            manager.removeBreakpoint(breakpoint)
        }
        log.info("C+ JS promoteToCucumberType: promoted ${vfile.name}:$line → ${type.id}")
        return true
    }

    override fun findBestPositionToAddBreakpoint(
        stepDefinitions: List<AbstractStepDefinition>,
    ): Pair<PsiElement, Int>? {

        // Iterate definitions in order — pick the first one that yields a usable
        // callback body line. Mirrors the Kotlin / Java extensions' behaviour.
        for (def in stepDefinitions) {
            val element = def.element ?: continue
            val call = findEnclosingCucumberCall(element) ?: continue
            val callback = findCallbackOf(call) ?: continue
            val target = firstExecutableLineOf(callback) ?: continue
            return target
        }
        return null
    }

    // ----- helpers ----------------------------------------------------------

    /**
     * Walks up from an arbitrary PSI element through every enclosing
     * [JSCallExpression] until it finds one whose callee is a cucumber-js helper
     * (`Given`, `When`, `Then`, `And`, `But`, `defineStep`, `Step`). Returns null
     * when no such call is found in the ancestor chain.
     *
     * The previous version stopped at the *innermost* call — so a breakpoint on
     * `jsValue = parseInt(start, 10);` inside a `Given(...)` callback resolved to
     * the `parseInt(...)` call, missed the `Given`, and returned null.
     */
    private fun findEnclosingCucumberCall(element: PsiElement): JSCallExpression? {
        var current: PsiElement? = element
        while (current != null) {
            val call = PsiTreeUtil.getParentOfType(current, JSCallExpression::class.java, false)
                ?: return null
            val callee = (call.methodExpression as? JSReferenceExpression)?.referenceName
            if (callee != null && callee in CUCUMBER_KEYWORDS) return call
            // Climb past this call and keep looking — the cucumber call might wrap
            // an inner one (e.g. `Given(..., () => { foo(parseInt(x)) })`).
            current = call.parent
        }
        return null
    }

    /** Returns the callback (`function(...) { … }` or `(…) => { … }`) of a Given/When/Then call. */
    private fun findCallbackOf(call: JSCallExpression): JSFunctionExpression? {
        // Walk the arguments looking for the FIRST JSFunctionExpression — typically
        // it's the last argument, but `Given(text, options, fn)` overloads exist.
        return call.arguments.firstNotNullOfOrNull { it as? JSFunctionExpression }
    }

    /**
     * First executable line of a JS / TS function body, skipping comments and
     * whitespace. Falls back to the function expression itself when the body is
     * empty or unreachable — the JVM debugger / Node.js inspector can still bind
     * to it but the line might not actually break.
     */
    private fun firstExecutableLineOf(fn: JSFunctionExpression): Pair<PsiElement, Int>? {
        val block = fn.block ?: return fn to (fn.getDocumentLine() ?: return null)
        var child: PsiElement? = block.firstChild
        while (child != null) {
            if (child !is PsiWhiteSpace && child !is PsiComment
                && child.node?.elementType?.toString() !in BRACE_NODES
            ) {
                val line = child.getDocumentLine() ?: return null
                return child to line
            }
            child = child.nextSibling
        }
        // Empty body — break on the closing brace so the debugger fires on return.
        val rBrace = block.lastChild ?: return null
        val line = rBrace.getDocumentLine() ?: return null
        return rBrace to line
    }

    /** Cheap textual check for a leading `@deprecated` JSDoc / TSDoc tag. */
    private fun hasDeprecatedJsDoc(element: PsiElement): Boolean {
        var prev: PsiElement? = element.prevSibling
        while (prev is PsiWhiteSpace || prev is PsiComment) {
            if (prev is PsiComment && prev.text.contains("@deprecated")) return true
            prev = prev.prevSibling
        }
        return false
    }

    companion object {
        private val CUCUMBER_KEYWORDS = setOf("Given", "When", "Then", "And", "But", "defineStep", "Step")
        private val BRACE_NODES = setOf("JS:LBRACE", "JS:RBRACE")
    }
}
