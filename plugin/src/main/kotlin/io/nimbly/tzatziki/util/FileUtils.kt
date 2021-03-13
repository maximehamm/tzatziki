package io.nimbly.tzatziki.util

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

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