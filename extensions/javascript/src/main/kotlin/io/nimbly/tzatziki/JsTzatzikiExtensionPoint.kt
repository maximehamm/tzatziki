/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * JavaScript / TypeScript implementation of [TzatzikiExtensionPoint] — wires the
 * Cucumber+ ⇄ JS/TS step-def synchronisation (breakpoint promotion, find-usages,
 * deprecation lookup).
 *
 * SKELETON — wires the extension into Gradle/`plugin-withJavaScript.xml` but every
 * callback is currently a no-op `null`. Implementation TODOs:
 *
 *  1. [findStepsAndBreakpoints] – from a `(VirtualFile, offset)` inside a JS / TS
 *     step-def file, locate the enclosing `Given(/regex/, fn)` / `When(...)` /
 *     `Then(...)` call (a `JSCallExpression` whose callee is one of the cucumber-js
 *     helpers), then collect the Gherkin steps that reference it via the
 *     cucumber-javascript plugin's PSI search.
 *
 *  2. [findBestPositionToAddBreakpoint] – return the first executable line inside
 *     the callback body (`JSFunctionExpression.body.statements[0]`).
 *
 *  3. [canRunStep] – delegate to the cucumber-javascript plugin's own step-defs.
 *
 *  4. [isDeprecated] – inspect JSDoc / TSDoc `@deprecated` annotations on the
 *     callback function or its containing variable.
 *
 *  See [io.nimbly.tzatziki.JavaTzatzikiExtensionPoint] / [KotlinTzatzikiExtensionPoint]
 *  for the JVM-side reference implementations.
 */
class JsTzatzikiExtensionPoint : TzatzikiExtensionPoint {

    override fun isDeprecated(element: PsiElement): Boolean {
        // TODO: walk to enclosing function / variable, read JSDoc `@deprecated`.
        return false
    }

    override fun canRunStep(stepDefinitions: List<AbstractStepDefinition>): Boolean {
        // TODO: return true when any definition originates from cucumber-javascript.
        //  The marker class is `org.jetbrains.plugins.cucumber.javascript.steps.JsStepDefinition`
        //  (verify against the plugin we depend on).
        return false
    }

    override fun findStepsAndBreakpoints(
        vfile: VirtualFile?,
        offset: Int?,
    ): Pair<List<GherkinStep>, List<XBreakpoint<*>>>? {
        // TODO: implement the JS / TS counterpart of JavaTzatzikiExtensionPoint —
        //  find the JSCallExpression of the surrounding `Given/When/Then(/.../, fn)`,
        //  fetch its `JSFunctionExpression` body, run `findStepUsages` against the
        //  cucumber-javascript search executor, return (steps, code-side breakpoints).
        return null
    }

    override fun findBestPositionToAddBreakpoint(
        stepDefinitions: List<AbstractStepDefinition>,
    ): Pair<PsiElement, Int>? {
        // TODO: from each definition, retrieve the JSFunctionExpression callback,
        //  walk to the first executable statement of its body, return (statement, line).
        return null
    }
}
