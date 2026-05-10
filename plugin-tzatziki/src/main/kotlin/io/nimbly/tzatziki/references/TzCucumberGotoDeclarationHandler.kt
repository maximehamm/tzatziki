/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Cmd+Click / Goto-Declaration handler for Gherkin steps.
 *
 * In modern IntelliJ ({@code GotoDeclarationAction}), if a {@code GotoDeclarationHandler}
 * returns a non-empty set of targets, the platform uses **only** those — it skips reference-based
 * target collection. This is the cleanest way for Cucumber+ to ensure scope-filtered step
 * definitions appear in the "Choose Declaration" popup, without IntelliJ aggregating the
 * unfiltered targets from JetBrains' default `CucumberStepReference`.
 *
 * Without this handler, both our `TzCucumberStepReference` (filtered) AND JetBrains'
 * `CucumberStepReference` (unfiltered) get queried, the platform unions their targets,
 * and the user sees duplicate / out-of-scope candidates in the popup.
 */
class TzCucumberGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {

        val element = sourceElement ?: return null
        val step = PsiTreeUtil.getParentOfType(element, GherkinStep::class.java, /* strict = */ false)
            ?: return null

        // Find OUR scope-aware reference among the step's references.
        val tzRef = step.references.firstOrNull { it is TzCucumberStepReference } as? TzCucumberStepReference
            ?: return null

        // resolveToDefinitions applies the scope filter (see TzCucumberStepReference).
        val targets = tzRef.resolveToDefinitions()
            .mapNotNull { it.element }
            .distinct()

        // Returning null tells the platform "we have nothing, fall through to references".
        // Returning an empty array tells the platform "we explicitly say there are no targets" —
        // also falls through. Either is fine, but returning null is the canonical "I don't handle
        // this element" signal.
        return if (targets.isEmpty()) null else targets.toTypedArray()
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null
}
