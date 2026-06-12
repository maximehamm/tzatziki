/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Opens the Cucumber+ rename dialog for [affected] and, on OK, applies the synchronised rename
 * (definition pattern + all bound Gherkin steps). Shared by the rename handler (Shift+F6) and the
 * proactive in-editor suggestion ([TzStepRenameSuggester]).
 */
fun promptStepRename(project: Project, affected: StepRename.Affected) {
    val dialog = TzRenameStepDialog(project, affected)
    if (!dialog.showAndGet()) return
    val newName = dialog.newName().takeIf { it.isNotEmpty() && it != affected.originalName } ?: return
    val defFile = affected.defElement.containingFile?.virtualFile
    WriteCommandAction.runWriteCommandAction(project, "Rename Cucumber Step", null, Runnable {
        StepRename.applyAffected(affected, newName)
    })
    refreshStepResolution(project, defFile)
}

/**
 * After rewriting a step-definition pattern, force the Cucumber step index + Gherkin resolution to
 * refresh. Editing a Kotlin (and some JS) definition's annotation does NOT promptly invalidate
 * cucumber's `loadStepsFor` cache, so the renamed Gherkin steps would otherwise show as "undefined"
 * until a manual reindex. Mirrors the existing `JsCucumberIndexRefresher` approach.
 */
private fun refreshStepResolution(project: Project, defFile: VirtualFile?) {
    defFile?.let { FileBasedIndex.getInstance().requestReindex(it) }
    DaemonCodeAnalyzer.getInstance(project).restart()
}

/**
 * Proactive-suggestion variant: the user has already typed the new step text in place (a separate
 * command). On confirm we (1) restore the step to its original text in its OWN command, then (2) run
 * a standard rename from that restored, resolvable step. A SINGLE undo of step (2) then reverts the
 * definition AND every Gherkin step back to the original — instead of leaving the step renamed but
 * the definition reverted. [stepPtr] is re-resolved between the two commands (it survives the reparse).
 */
fun promptStepRenameRestoringFirst(
    project: Project,
    previewAffected: StepRename.Affected,
    stepPtr: SmartPsiElementPointer<GherkinStep>,
    originalName: String,
) {
    val dialog = TzRenameStepDialog(project, previewAffected)
    if (!dialog.showAndGet()) return
    val newName = dialog.newName().takeIf { it.isNotEmpty() && it != originalName } ?: return
    val defFile = previewAffected.defElement.containingFile?.virtualFile
    WriteCommandAction.runWriteCommandAction(project, "Rename Cucumber Step", null, Runnable {
        stepPtr.element?.let { StepRename.restoreStepName(it, originalName) }
    })
    WriteCommandAction.runWriteCommandAction(project, "Rename Cucumber Step", null, Runnable {
        stepPtr.element?.let { StepRename.apply(it, newName) }
    })
    refreshStepResolution(project, defFile)
}

/**
 * Intercepts the rename of a Gherkin step (Shift+F6 / in-place) BEFORE cucumber's
 * `GherkinStepRenameHandler`, which mangles the rename: it bakes the concrete parameter value into
 * the step definition (`{int}` → `5`, `{string}` → `"hello"`) and DROPS the step's attached data
 * table / doc-string.
 *
 * cucumber's handler is registered WITHOUT an `order`, so registering this one with `order="first"`
 * makes the rename registry pick it first. We route to the table/parameter/doc-string-safe
 * [StepRename.apply]. When we can't handle the step ([StepRename.canRename] is false) we report
 * unavailable so the platform keeps its own behaviour.
 */
class TzGherkinStepRenameHandler : RenameHandler {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val step = stepFrom(dataContext) ?: return false
        return StepRename.canRename(step)
    }

    override fun isRenaming(dataContext: DataContext): Boolean = isAvailableOnDataContext(dataContext)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        val step = dataContext?.let { stepFrom(it) }
            ?: (editor?.let { stepAt(file, it) })
            ?: return
        doRename(project, step)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        val step = elements.firstOrNull() as? GherkinStep
            ?: dataContext?.let { stepFrom(it) }
            ?: return
        doRename(project, step)
    }

    private fun doRename(project: Project, step: GherkinStep) {
        val affected = StepRename.affected(step) ?: return
        promptStepRename(project, affected)
    }

    private fun stepFrom(dataContext: DataContext): GherkinStep? {
        (CommonDataKeys.PSI_ELEMENT.getData(dataContext) as? PsiElement)
            ?.let { PsiTreeUtil.getParentOfType(it, GherkinStep::class.java, false) }
            ?.let { return it }
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(dataContext)
        return stepAt(file, editor)
    }

    private fun stepAt(file: PsiFile?, editor: Editor): GherkinStep? {
        val element = file?.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, GherkinStep::class.java, false)
    }
}
