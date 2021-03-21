package io.nimbly.tzatziki.psi

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

fun PsiFile.getModule()
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