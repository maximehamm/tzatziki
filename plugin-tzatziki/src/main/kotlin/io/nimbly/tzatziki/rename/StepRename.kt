/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.findCucumberStepReference
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Orchestration of the synchronised step rename (feature #8) — wires together:
 *   resolve the step → step definition,
 *   read the definition's pattern (per-language, via TzatzikiExtensionPoint),
 *   collect the sibling Gherkin steps bound to the same definition,
 *   run the pure [StepRenameEngine],
 *   then apply: rewrite the step-def pattern + every Gherkin step name.
 *
 * Only the step NAME (text after the keyword) is touched — keyword, data tables and doc-strings
 * are left intact. This is the back-end the UX (rename handler / proactive in-editor suggestion)
 * drives; it is also directly unit/integration-testable.
 */
object StepRename {

    /**
     * Apply the synchronised rename of [step] to [newName]. Returns the applied [RenameResult]
     * (the new pattern + new sibling names) or `null` if it can't be done safely (unresolved step,
     * unsupported pattern, a value rather than a literal changed, …).
     *
     * MUST be called inside a write command action.
     */
    fun apply(step: GherkinStep, newName: String): RenameResult? {
        val (defElement, pattern) = renamableDef(step) ?: return null
        val oldName = step.name?.takeIf { it.isNotBlank() } ?: return null
        return applyPlan(plan(step, defElement, pattern, oldName, newName) ?: return null, step.project)
    }

    /**
     * Apply a rename whose definition was captured BEFORE the live step was altered (the proactive
     * suggestion): the edited step no longer resolves, so we drive the rename from the snapshot's
     * definition + the step's ORIGINAL name held in [Affected.originalName].
     *
     * MUST be called inside a write command action.
     */
    fun applyAffected(affected: Affected, newName: String): RenameResult? {
        val plan = plan(affected.editedStep, affected.defElement, affected.pattern, affected.originalName, newName) ?: return null
        return applyPlan(plan, affected.editedStep.project)
    }

    private fun applyPlan(plan: Plan, project: Project): RenameResult? {
        // 1) Rewrite the step-definition pattern (per-language PSI).
        if (!plan.extensionRewrite(plan.newPattern)) return null

        // 2) Rewrite the Gherkin step names (edited step + siblings), grouped per document,
        //    applied high-offset-first so earlier offsets stay valid.
        val byDoc = LinkedHashMap<Document, MutableList<Pair<TextRange, String>>>()
        for ((range, text, doc) in plan.gherkinEdits)
            byDoc.getOrPut(doc) { mutableListOf() }.add(range to text)
        val pdm = PsiDocumentManager.getInstance(project)
        for ((doc, edits) in byDoc) {
            edits.sortedByDescending { it.first.startOffset }
                .forEach { (range, text) -> doc.replaceString(range.startOffset, range.endOffset, text) }
            pdm.commitDocument(doc)
        }
        return plan.result
    }

    /** The elements a rename of [editedStep] will touch: the step definition + every Gherkin step
     *  bound to it (the edited one first, then siblings). Read-only — for the rename preview UI. */
    class Affected(
        val defElement: PsiElement,
        val pattern: StepPatternInfo,
        val editedStep: GherkinStep,
        /** Edited step first, then siblings. */
        val steps: List<GherkinStep>,
        /** The step name the definition currently matches (pre-edit). Defaults to the live name;
         *  the proactive suggestion overrides it because the live step is already altered. */
        val originalName: String = editedStep.name ?: "",
        /** Text to pre-fill the rename field with (the already-typed new text, if any). */
        val initialText: String = originalName,
    ) {
        val siblings: List<GherkinStep> get() = steps.filter { it !== editedStep }
    }

    /** Resolve everything a rename of [step] would affect (read-only), or `null` if not renamable. */
    fun affected(step: GherkinStep): Affected? {
        val (defElement, info) = renamableDef(step) ?: return null
        val matching = projectGherkinSteps(step).filter { StepRenameEngine.valuesOf(info.raw, info.kind, it.name) != null }
        val ordered = listOf(step) + matching.filter { it !== step }
        return Affected(defElement, info, step, ordered)
    }

    /** Like [affected], but using a definition captured BEFORE [editedStep] was altered (proactive
     *  suggestion). [originalName] is the name the definition still matches; the edited step's
     *  CURRENT (altered) text becomes the pre-filled new name. */
    fun affectedFrom(defElement: PsiElement, pattern: StepPatternInfo, editedStep: GherkinStep, originalName: String): Affected? {
        if (StepRenameEngine.segment(pattern.raw, pattern.kind) == null) return null
        val matching = projectGherkinSteps(editedStep)
            .filter { it !== editedStep && StepRenameEngine.valuesOf(pattern.raw, pattern.kind, it.name) != null }
        return Affected(defElement, pattern, editedStep, listOf(editedStep) + matching, originalName, editedStep.name ?: "")
    }

    /**
     * True if our engine can drive the rename of [step] — it resolves to a step definition whose
     * pattern is owned by a [io.nimbly.tzatziki.TzatzikiExtensionPoint] and is segmentable.
     */
    fun canRename(step: GherkinStep): Boolean = renamableDef(step) != null

    /**
     * Restore [step]'s NAME to [name] (only the name range; keyword / table / doc-string untouched).
     * Used by the proactive suggestion to rewind the user's in-place edit in its OWN command, so the
     * subsequent rename's undo baseline is the original text → a single undo reverts everything.
     *
     * MUST be called inside a write command action.
     */
    fun restoreStepName(step: GherkinStep, name: String): Boolean {
        if (step.name == name) return true
        val pdm = PsiDocumentManager.getInstance(step.project)
        val (range, text, doc) = nameEdit(step, name, pdm) ?: return false
        doc.replaceString(range.startOffset, range.endOffset, text)
        pdm.commitDocument(doc)
        return true
    }

    /**
     * Resolve [step] → its (definition element, pattern) IF the engine can drive a rename, else
     * `null`. Lightweight: NO project-wide sibling scan — safe to call on caret movement to snapshot
     * a step before the user starts editing it.
     */
    fun renamableDef(step: GherkinStep): Pair<PsiElement, StepPatternInfo>? {
        val defElement = step.findCucumberStepReference()?.resolveToDefinitions()?.firstOrNull()?.element ?: return null
        val info = Tzatziki().extensionList.firstNotNullOfOrNull { it.getStepPattern(defElement) } ?: return null
        if (StepRenameEngine.segment(info.raw, info.kind) == null) return null
        return defElement to info
    }

    // --- planning (read-only; reusable by the UX to decide whether to offer the rename) ---------

    private class Plan(
        val result: RenameResult,
        val newPattern: String,
        val extensionRewrite: (String) -> Boolean,
        val gherkinEdits: List<Triple<TextRange, String, Document>>,
    )

    private fun plan(editedStep: GherkinStep, defElement: PsiElement, pattern: StepPatternInfo, oldName: String, newName: String): Plan? {
        val project = editedStep.project
        if (oldName.isBlank() || newName.isBlank() || newName == oldName) return null

        // The language extension that owns this definition (re-found from the def element).
        val extension = Tzatziki().extensionList.firstNotNullOfOrNull { ext ->
            ext.getStepPattern(defElement)?.let { ext }
        } ?: return null

        // Sibling Gherkin steps = every step the engine can rename the SAME way (i.e. whose name
        // matches this pattern). Engine-matching (rather than strict resolution) also catches
        // Scenario-Outline steps with <placeholders>. Scanned across all project feature files.
        val siblings = projectGherkinSteps(editedStep)
            .filter { it !== editedStep && StepRenameEngine.valuesOf(pattern.raw, pattern.kind, it.name) != null }

        val result = StepRenameEngine.rename(
            pattern = pattern.raw, kind = pattern.kind,
            oldStepText = oldName, newStepText = newName, siblings = siblings.map { it.name },
        ) ?: return null

        // Build the per-step Gherkin edits (name text-range → new name).
        val pdm = PsiDocumentManager.getInstance(project)
        val edits = mutableListOf<Triple<TextRange, String, Document>>()
        nameEdit(editedStep, newName, pdm)?.let { edits += it }
        siblings.forEachIndexed { i, sib ->
            if (i < result.newSiblings.size) nameEdit(sib, result.newSiblings[i], pdm)?.let { edits += it }
        }

        return Plan(result, result.newPattern, { extension.rewriteStepPattern(defElement, it) }, edits)
    }

    /** Every [GherkinStep] in every feature file of the project (scope for sibling matching). */
    private fun projectGherkinSteps(anchor: GherkinStep): List<GherkinStep> {
        val project = anchor.project
        val psiManager = PsiManager.getInstance(project)
        val result = mutableListOf<GherkinStep>()
        FileTypeIndex.getFiles(GherkinFileType.INSTANCE, GlobalSearchScope.projectScope(project)).forEach { vf ->
            psiManager.findFile(vf)?.let { result += PsiTreeUtil.findChildrenOfType(it, GherkinStep::class.java) }
        }
        // The edited step's own (possibly in-memory / unsaved) file may not be indexed yet — include it.
        anchor.containingFile?.let { result += PsiTreeUtil.findChildrenOfType(it, GherkinStep::class.java) }
        return result.distinct()
    }

    /** The (range, newName, document) edit that replaces just the step's NAME (keyword and any
     *  attached table / doc-string are outside this range). `null` if it can't be located. */
    private fun nameEdit(step: GherkinStep, newName: String, pdm: PsiDocumentManager): Triple<TextRange, String, Document>? {
        val name = step.name?.takeIf { it.isNotEmpty() } ?: return null
        val rel = step.text.indexOf(name)          // keyword precedes; first occurrence is the name
        if (rel < 0) return null
        val start = step.textRange.startOffset + rel
        val doc = pdm.getDocument(step.containingFile) ?: return null
        return Triple(TextRange(start, start + name.length), newName, doc)
    }
}
