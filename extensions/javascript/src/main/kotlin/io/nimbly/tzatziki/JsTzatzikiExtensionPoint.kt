/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.nimbly.tzatziki.util.findProject
import io.nimbly.tzatziki.util.findStepUsages
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.getFile
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptExtension
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

        val call = findEnclosingCucumberCall(element) ?: run {
            log.info("C+ JS findStepsAndBreakpoints: bail — no enclosing Given/When/Then call at ${vfile.name}@$offset")
            return null
        }
        val callback = findCallbackOf(call) ?: run {
            log.info("C+ JS findStepsAndBreakpoints: bail — no JSFunctionExpression callback in ${call.text.take(60)}")
            return null
        }

        // The cucumber-javascript plugin doesn't bind Gherkin step references to the
        // raw JSCallExpression — it indexes step-defs as JSImplicitElementImpl stubs
        // (created in CucumberJavaScriptExtension.loadStepsFor) and resolves Gherkin
        // refs to THOSE stubs. So `ReferencesSearch.search(call)` returns 0 hits.
        // We round-trip through the cucumber-js extension to grab the stub for our
        // call's location, then search references from there.
        val module = ModuleUtilCore.findModuleForFile(file)
        val allDefs = CucumberJavaScriptExtension().loadStepsFor(file, module)
        val ourDef = allDefs.firstOrNull { def ->
            val el = def.element ?: return@firstOrNull false
            // The stub keeps its textOffset inside the regex/string literal first
            // argument of the call, so the call's textRange contains it.
            call.textRange.contains(el.textOffset)
        }
        val stepReferences = ourDef?.element?.let { findStepUsages(it) } ?: emptyList()
        val steps = stepReferences.map { it.element }.filterIsInstance<GherkinStep>()

        // Existing breakpoints whose source position falls inside the callback body.
        val callbackRange = callback.textRange
        val callbackVfile = callback.containingFile?.originalFile?.virtualFile
        val allBreakpoints = XDebuggerManager.getInstance(project).breakpointManager
            .allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter {
                val sp = it.sourcePosition ?: return@filter false
                sp.file == callbackVfile && callbackRange.contains(sp.offset)
            }

        log.info("C+ JS findStepsAndBreakpoints: file=${vfile.name} offset=$offset call='${call.text.take(40)}' defs=${allDefs.size} ourDef=${ourDef?.let { it.element?.text?.take(40) ?: "<no-element>" }} refs=${stepReferences.size} steps=${steps.size} bps=${allBreakpoints.size}")
        return steps to allBreakpoints
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
