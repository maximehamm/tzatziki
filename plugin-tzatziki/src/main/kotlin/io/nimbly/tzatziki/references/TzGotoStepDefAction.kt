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

import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.SwingConstants
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Replaces the standard {@code GotoDeclaration} action when the cursor is on a Gherkin step.
 *
 * Two reasons to override:
 *  1. Apply the {@link io.nimbly.tzatziki.services.StepScope} filter via our
 *     [TzCucumberStepReference] (already done by [TzCucumberGotoDeclarationHandler], but having
 *     the action handle it directly removes any ambiguity if the platform also queries other
 *     handlers / references).
 *  2. When ambiguity remains after filtering (e.g. a project without `.cucumber-scope` files),
 *     show a custom popup with a {@code setAdText} footer suggesting the `.cucumber-scope`
 *     mechanism — visible UX feedback for issue #104.
 *
 * For non-Gherkin contexts (any other file type), we delegate to the original action.
 */
class TzGotoStepDefAction : GotoDeclarationAction() {

    init {
        // Fallback for IntelliJ 2026.1.x EAP, which validates action text strictly at
        // menu paint time and throws PluginException ("Empty menu item text") otherwise
        // — that exception was retried on every menu repaint and caused 20s+ editor
        // freezes (issue #124). `plugin.xml` already sets these, but populating the
        // template presentation here guards against any registration timing race.
        templatePresentation.text = "Go to Declaration"
        templatePresentation.description = "Navigate to the declaration of a symbol"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (project == null || editor == null || psiFile !is GherkinFile) {
            super.actionPerformed(e)
            return
        }

        val offset = editor.caretModel.offset
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val element = psiFile.findElementAt(offset)
        val step = PsiTreeUtil.getParentOfType(element, GherkinStep::class.java, /* strict = */ false)
        if (step == null) {
            super.actionPerformed(e)
            return
        }

        // Pick our reference (priority registered HIGHER) and resolve through the scope filter.
        val tzRef = step.references.firstOrNull { it is TzCucumberStepReference } as? TzCucumberStepReference
        if (tzRef == null) {
            super.actionPerformed(e)
            return
        }

        val targets = tzRef.resolveToDefinitions()
            .mapNotNull { it.element }
            .distinct()
            .filterIsInstance<NavigatablePsiElement>()
            .toTypedArray()

        when (targets.size) {
            0 -> super.actionPerformed(e)
            1 -> targets[0].navigate(true)
            else -> {
                val popup = PsiElementListNavigator.navigateOrCreatePopup(
                    targets,
                    "Choose Step Definition",
                    "Step definitions of '${step.name}'",
                    DefaultPsiElementCellRenderer(),
                    null
                )
                if (popup != null) {
                    // Footer hint: encourage the user to drop a .cucumber-scope file to disambiguate.
                    popup.setAdText(
                        "Tip: drop a .cucumber-scope file at the root of an app folder to filter step definitions per app.",
                        SwingConstants.LEFT
                    )
                    popup.showInBestPositionFor(editor)
                }
            }
        }
    }
}
