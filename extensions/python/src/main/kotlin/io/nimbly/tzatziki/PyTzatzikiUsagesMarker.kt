/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import io.nimbly.tzatziki.usages.TzStepsUsagesMarker
import io.nimbly.tzatziki.util.findCucumberStepDefinitions
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Python / behave counterpart of [io.nimbly.tzatziki.JavaTzatzikiUsagesMarker] &
 * [io.nimbly.tzatziki.JsTzatzikiUsagesMarker]: shows a "N scenarios" gutter
 * line-marker on each behave step definition — a top-level function decorated
 * with `@given/@when/@then/@step("…")` — with a navigation popup to the Gherkin
 * steps that resolve to it.
 *
 * Reverse search: a plain `ReferencesSearch` from the function finds nothing
 * (Gherkin steps don't contain the function name, so the word index misses
 * them — same limitation the JS extension hit). So we enumerate every `.feature`
 * file and keep the steps whose resolved step-definition lands inside one of the
 * step-def functions in this marker batch. Forward resolution is provided by the
 * Python plugin's behave integration.
 */
class PyTzatzikiUsagesMarker : TzStepsUsagesMarker() {

    private val behaveDecorators = setOf("given", "when", "then", "step", "and", "but")

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        // Step-def functions in this batch, anchored on their name identifier leaf.
        val functions: List<Pair<LeafPsiElement, PyFunction>> = elements
            .filterIsInstance<LeafPsiElement>()
            .mapNotNull { leaf ->
                val fn = leaf.parent as? PyFunction ?: return@mapNotNull null
                if (fn.nameIdentifier !== leaf) return@mapNotNull null
                if (!isBehaveStep(fn)) return@mapNotNull null
                leaf to fn
            }
        if (functions.isEmpty()) return

        val project = functions.first().second.project
        DumbService.getInstance(project).runReadActionInSmartMode {
            val stepsByFunction = collectGherkinSteps(project, functions.map { it.second })
            for ((leaf, fn) in functions) {
                val steps = stepsByFunction[fn]?.takeIf { it.isNotEmpty() } ?: continue
                result.add(buildMarker(element = leaf, targets = steps))
            }
        }
    }

    private fun isBehaveStep(fn: PyFunction): Boolean {
        val decorators = fn.decoratorList?.decorators ?: return false
        return decorators.any { it.name?.lowercase() in behaveDecorators }
    }

    /** Enumerate all Gherkin steps and bucket them by the step-def function they
     *  resolve to (restricted to [functions]). */
    private fun collectGherkinSteps(
        project: com.intellij.openapi.project.Project,
        functions: List<PyFunction>,
    ): Map<PyFunction, MutableList<GherkinStep>> {
        val result = HashMap<PyFunction, MutableList<GherkinStep>>()
        val gherkinFiles = FileTypeIndex.getFiles(
            GherkinFileType.INSTANCE, GlobalSearchScope.projectScope(project),
        )
        val psiManager = PsiManager.getInstance(project)
        for (gvfile in gherkinFiles) {
            val gpsiFile = psiManager.findFile(gvfile) ?: continue
            PsiTreeUtil.findChildrenOfType(gpsiFile, GherkinStep::class.java).forEach { step ->
                val defElements = step.findCucumberStepDefinitions().mapNotNull { it.element }
                if (defElements.isEmpty()) return@forEach
                for (fn in functions) {
                    val matched = defElements.any { it === fn || PsiTreeUtil.isAncestor(fn, it, false) }
                    if (matched) result.getOrPut(fn) { mutableListOf() }.add(step)
                }
            }
        }
        return result
    }
}
