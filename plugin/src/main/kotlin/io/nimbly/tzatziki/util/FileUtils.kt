package io.nimbly.tzatziki.util

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

fun PsiFile.cellAt(offset: Int): GherkinTableCell? {
    var element = findElementAt(offset) ?: return null
    if (element is GherkinTableCell)
        return element

    if (element is LeafPsiElement && element.parent is GherkinTableCell)
        return element.parent as GherkinTableCell

    if (element is LeafPsiElement && element.parent is PsiWhiteSpace)
        element = element.parent
    if (element is LeafPsiElement && element.prevSibling is PsiWhiteSpace)
        element = element.prevSibling
    if (element is LeafPsiElement && element.nextSibling is PsiWhiteSpace)
        element = element.nextSibling

    if (element is LeafPsiElement && element.nextSibling == null && element.parent is GherkinTableRow)
        return (element.parent as GherkinTableRow).psiCells.last()

    if (element is PsiWhiteSpace && element.parent is GherkinTableCell)
        return element.parent as GherkinTableCell
    if (element is PsiWhiteSpace && element.prevSibling is GherkinTableCell)
        return element.prevSibling as GherkinTableCell
    if (element is PsiWhiteSpace && element.nextSibling is GherkinTableCell)
        return element.nextSibling as GherkinTableCell

    return null
}