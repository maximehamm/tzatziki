package io.nimbly.tzatziki.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

fun PsiElement.getDocument(): Document? {
    val containingFile = containingFile ?: return null
    var file = containingFile.virtualFile
    if (file == null) {
        file = containingFile.originalFile.virtualFile
    }
    return if (file == null) null else
        FileDocumentManager.getInstance().getDocument(file)
}

fun PsiElement.getDocumentLine()
    = getDocument()?.getLineNumber(textOffset)

fun Document.getColumnAt(offset: Int): Int {
    val line = getLineNumber(offset)
    return offset - getLineStartOffset(line)
}

fun  Document.getTextLineBefore(offset: Int): String {
    val line = getLineNumber(offset)
    val lineStart = getLineStartOffset(line)
    return getText(TextRange(lineStart, offset))
}

fun  Document.getTextLineAfter(offset: Int): String {
    val line = getLineNumber(offset)
    val lineEnd = getLineEndOffset(line)
    return getText(TextRange(offset, lineEnd))
}