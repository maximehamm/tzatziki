/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.usages.TzStepsUsagesMarker

/**
 * JS / TS counterpart of [io.nimbly.tzatziki.JavaTzatzikiUsagesMarker]: shows a
 * "N scenarios" gutter line-marker on each cucumber-js step definition
 * (`Given(/regex/, fn)` / `When("…", fn)` / `Then(…)`), with a navigation popup
 * to the Gherkin steps that resolve to it.
 *
 * The marker is anchored on the callee identifier leaf (`Given` / `When` / …)
 * of the call. Gherkin steps are resolved by reusing the reverse-search already
 * implemented in [JsTzatzikiExtensionPoint] via `Tzatziki.findSteps(vfile, offset)`,
 * so the marker and the breakpoint sync share one resolution code path.
 */
class JsTzatzikiUsagesMarker : TzStepsUsagesMarker() {

    private val cucumberKeywords = setOf("Given", "When", "Then", "And", "But", "defineStep", "Step")

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        for (element in elements) {
            // Only leaf elements — attaching markers to composite PSI triggers a
            // platform warning. Identify the callee identifier of a cucumber call.
            if (element.firstChild != null) continue

            // leaf "Given" → JSReferenceExpression (callee) → JSCallExpression
            val refExpr = element.parent as? JSReferenceExpression ?: continue
            if (refExpr.referenceName !in cucumberKeywords) continue
            val call = refExpr.parent as? JSCallExpression ?: continue
            if (call.methodExpression !== refExpr) continue

            // Confirm the call actually looks like a step-def: first argument is a
            // regex or string literal (the step pattern). Skips unrelated calls
            // named Given/When/Then that aren't cucumber-js step definitions.
            val firstArg = call.arguments.firstOrNull() ?: continue
            val argType = firstArg.javaClass.simpleName
            if (!argType.contains("Literal", ignoreCase = true) &&
                !argType.contains("RegExp", ignoreCase = true)
            ) continue

            val vfile = element.containingFile?.virtualFile ?: continue
            val token = element

            DumbService.getInstance(element.project).runReadActionInSmartMode {
                val steps = Tzatziki.findSteps(vfile, token.textOffset)
                if (steps.isNotEmpty()) {
                    result.add(buildMarker(element = token, targets = steps))
                }
            }
        }
    }
}
