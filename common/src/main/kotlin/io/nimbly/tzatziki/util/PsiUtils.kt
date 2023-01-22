/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.util

import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER
import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import org.jetbrains.plugins.cucumber.steps.search.CucumberStepSearchUtil

fun VirtualFile.getFile(project: Project): PsiFile?
    = PsiManager.getInstance(project).findFile(this)

fun VirtualFile.getDirectory(project: Project): PsiDirectory?
    = PsiManager.getInstance(project).findDirectory(this)

fun PsiFile.getModule(): Module?
    = ModuleUtilCore.findModuleForPsiElement(this)

fun PsiFile.cellAt(offset: Int): GherkinTableCell? {
    var l = findElementAt(offset) ?: return null
    if (l is GherkinTableCell)
        return l

    if (l is LeafPsiElement && l.parent is GherkinTableCell)
        return l.parent as GherkinTableCell
    if (l is LeafPsiElement && l.prevSibling is GherkinTableCell)
        return l.prevSibling as GherkinTableCell
    if (l is LeafPsiElement && l.nextSibling is GherkinTableCell)
        return l.nextSibling as GherkinTableCell

    if (l is LeafPsiElement && l.parent is PsiWhiteSpace)
        l = l.parent
    if (l is LeafPsiElement && l.prevSibling is PsiWhiteSpace)
        l = l.prevSibling
    if (l is LeafPsiElement && l.nextSibling is PsiWhiteSpace)
        l = l.nextSibling

    if (l is LeafPsiElement && l.nextSibling == null && l.parent is GherkinTableRow)
        return (l.parent as GherkinTableRow).psiCells.last()

    if (l is PsiWhiteSpace && l.parent is GherkinTableCell)
        return l.parent as GherkinTableCell
    if (l is PsiWhiteSpace && l.prevSibling is GherkinTableCell)
        return l.prevSibling as GherkinTableCell
    if (l is PsiWhiteSpace && l.nextSibling is GherkinTableCell)
        return l.nextSibling as GherkinTableCell

    return null
}

fun PsiFile.isColumnSeletionModeZone(offset: Int): Boolean {
    val line = PsiTreeUtil.findElementOfClassAtOffset(
        this, offset,
        GherkinTableRow::class.java, false
    ) ?: return false
    return !(offset < line.startOffset || offset > line.endOffset)
}

fun PsiElement.getDocument(): Document? {
    val containingFile = containingFile ?: return null
    var file = containingFile.virtualFile
    if (file == null) {
        file = containingFile.originalFile.virtualFile
    }
    return if (file == null) null else
        FileDocumentManager.getInstance().getDocument(file)
}

val PsiElement.previousPipe: PsiElement
    get() {
        var el = prevSibling
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                return el
            }
            el = el.prevSibling
        }
        throw Exception("Psi structure corrupted !")
    }
val PsiElement.nextPipe: PsiElement
    get() {
        var el = nextSibling
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                return el
            }
            el = el.nextSibling
        }
        throw Exception("Psi structure corrupted !")
    }

fun PsiElement.getDocumentLine()
    = getDocument()?.getLineNumber(textOffset)

val PsiElement.safeText
    get() = text.safeText

val String.safeText
    get() = this.replace(DUMMY_IDENTIFIER, "", true)
        .replace(DUMMY_IDENTIFIER_TRIMMED, "", true)


fun PsiElement.collectReferences(referencesSearchScope: SearchScope): Collection<PsiReference> {

    val search = ReferencesSearch.search(
        this, referencesSearchScope, false)

    val processor: CommonProcessors.CollectProcessor<PsiReference> =
        object : CommonProcessors.CollectProcessor<PsiReference>() {
            override fun accept(reference: PsiReference): Boolean {
                return true
            }
        }

    search.forEach(processor)
    return processor.results
}

fun findStepUsages(element: PsiElement): List<PsiReference> {

    val scope = CucumberStepSearchUtil.restrictScopeToGherkinFiles(GlobalSearchScope.projectScope(element.project))
    val search = ReferencesSearch.search(element, scope, false)

    val references = mutableListOf<PsiReference>()
    search.forEach(Processor { ref: PsiReference ->
        val elt = ref.element
        if (elt is GherkinStep && ref is CucumberStepReference)
            references.add(ref)
        true
    })

    return references
}

fun PsiElement.up(level: Int): PsiElement {
    var p = this
    for (i in 1..level)
        p = p.parent
    return p
}

inline fun <reified T: PsiElement> PsiElement.findPreviousSiblingsOfType(): List<T> {
    val found = mutableListOf<T>()
    var elt = this.node
    while (elt != null) {
        val psi = elt.psi
        if (psi is T)
            found.add(psi)
        if (elt.treePrev == elt.treeParent)
            elt = null
        else
            elt = elt.treePrev
    }
    return found
}

fun String.ellipsis(length: Int): String {
    var t = this.take(length)
    if (t.length < this.length)
        t += "..."
    return t
}