/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package io.nimbly.tzatziki.references

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import io.nimbly.tzatziki.services.StepScope
import io.nimbly.tzatziki.services.TzScopeAdvisor
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

/**
 * Cucumber+ extension of the standard JetBrains [CucumberStepReference] that adds:
 *
 *  1. **Step-scope filtering** (issue #104): when a `.cucumber-scope` marker file is found
 *     by walking up from the .feature file, only step definitions located in the same anchor
 *     directory are kept. If filtering would drop everything, we fall back to the unfiltered
 *     set so the user is never left with zero matches because of the heuristic.
 *
 *  2. **Last-valid result fallback**: when the current resolution is empty (e.g. a transient
 *     PSI hiccup or a renamed step def), reuse the last non-empty result we cached on the
 *     step PSI element via copyable user data.
 *
 * This class deliberately **extends** the JetBrains class (rather than re-implementing
 * [com.intellij.psi.PsiPolyVariantReference]) so the many `ref is CucumberStepReference`
 * checks scattered across both Cucumber+ and the cucumber-jvm plugin keep working — they
 * accept our subclass naturally.
 */
class TzCucumberStepReference(step: PsiElement, range: TextRange) : CucumberStepReference(step, range) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {

        // Derive multiResolve from our own resolveToDefinitions so both paths are guaranteed
        // to be consistent. Calling super.multiResolve() goes through a private multiResolveInner
        // and a separate ResolveCache, which can drift out of sync with our filter (e.g. stale
        // cache or different dedup semantics).
        val results = resolveToDefinitions()
            .mapNotNull { it.element }
            .distinct()
            .map { PsiElementResolveResult(it) as ResolveResult }
            .toTypedArray()

        // Suggest dropping a `.cucumber-scope` file when ambiguity remains (one-shot per project).
        TzScopeAdvisor.maybeAdviseAboutCucumberScope(element.project, results.size)

        // Last-valid fallback so navigation keeps working through transient empty resolutions.
        if (results.isEmpty()) {
            val lastValid = element.getCopyableUserData(LAST_VALID)
            if (lastValid != null) return lastValid
        } else {
            element.putCopyableUserData(LAST_VALID, results)
        }
        return results
    }

    /**
     * Re-implements [resolveToDefinitions] without delegating to the parent — JetBrains'
     * {@code CucumberStepHelper.findStepDefinitions} de-duplicates step defs *by class*
     * ({@code Map<Class, AbstractStepDefinition>}). When two step defs share both the same
     * class (e.g. {@code JavaAnnotatedStepDefinition}) AND the same regex (the #104
     * homonym case), only the first one encountered survives — which is wrong: we lose
     * the legitimate alternative living in another app folder.
     *
     * Iterating {@code loadStepsFor} ourselves preserves all matching definitions, then
     * we apply the scope filter on top. This path is used by breakpoint synchronization,
     * the Gherkin annotator, the undefined-step inspection, etc.
     */
    override fun resolveToDefinitions(): Collection<AbstractStepDefinition> {
        val step = element as? GherkinStep ?: return super.resolveToDefinitions()
        val module = ModuleUtilCore.findModuleForPsiElement(step) ?: return emptyList()
        val featureFile = step.containingFile ?: return emptyList()

        // Collect every framework's matching step definitions, deduped by their PsiElement
        // (i.e. by the actual @Given/@When method) — NOT by class.
        val matched = LinkedHashMap<PsiElement, AbstractStepDefinition>()
        for (extension in CucumberJvmExtensionPoint.EP_NAME.extensionList) {
            val name = extension.getStepName(step) ?: continue
            extension.loadStepsFor(featureFile, module)
                ?.filterNotNull()
                ?.filter { it.supportsStep(step) && it.matches(name) }
                ?.forEach { def ->
                    val key = def.element ?: return@forEach
                    matched.putIfAbsent(key, def)
                }
        }
        val all = matched.values.toList()

        // Apply scope filter strictly.
        // - When the feature file is under a `.cucumber-scope` anchor, candidates outside
        //   that anchor are dropped — even if that drops everything (otherwise an undefined
        //   step in App A would resolve to App B's identically-named step def, and the
        //   "missing step" inspection would stay silent).
        // - When no anchor governs the feature file, `isInSameScope` is permissive and
        //   `filtered` == `all`, so nothing changes.
        val featureVf = featureFile.virtualFile
        val project = element.project
        return all.filter { def ->
            val candidateVf = def.element?.containingFile?.virtualFile
            StepScope.isInSameScope(featureVf, candidateVf, project)
        }
    }

    companion object {
        private val LAST_VALID = Key<Array<ResolveResult>>("LAST_VALID")
    }
}
