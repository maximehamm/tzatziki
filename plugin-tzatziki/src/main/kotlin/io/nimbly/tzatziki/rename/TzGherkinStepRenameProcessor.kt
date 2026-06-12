/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Overrides the platform / cucumber Gherkin step rename (`GherkinStepRenameProcessor` →
 * `GherkinStepImpl.setName`), which has three regressions when renaming a step:
 *   1. it BAKES the concrete parameter value into the step definition (`{int}` → `5`, `{string}`
 *      → `"hello"`), destroying the parameterised definition;
 *   2. it DROPS the step's attached data table;
 *   3. it DROPS the step's attached doc-string.
 *
 * We take over (with higher EP order) only when our engine can drive the rename
 * ([StepRename.canRename]) and route to [StepRename.apply], which preserves parameters, data tables
 * and doc-strings, and propagates the rename to the definition pattern + all sibling steps.
 * When we can't handle it, [canProcessElement] returns false and the platform keeps its behaviour.
 */
class TzGherkinStepRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean =
        element is GherkinStep && StepRename.canRename(element)

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<out UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val step = element as? GherkinStep ?: return
        StepRename.apply(step, newName)
        listener?.elementRenamed(step)
    }
}
