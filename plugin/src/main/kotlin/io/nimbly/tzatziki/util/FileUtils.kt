package io.nimbly.tzatziki.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.util.ArrayList

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