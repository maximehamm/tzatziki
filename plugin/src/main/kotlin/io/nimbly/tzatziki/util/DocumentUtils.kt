package io.nimbly.tzatziki.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

fun Document.getLineStart(offset: Int)
    = getLineStartOffset(getLineNumber(offset))

fun Document.getLineEnd(offset: Int)
    = getLineEndOffset(getLineNumber(offset))

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

fun  Document.getTextLine(offset: Int): String {
    val line = getLineNumber(offset)
    val lineStart = getLineStartOffset(line)
    val lineEnd = getLineEndOffset(line)
    return getText(TextRange(lineStart, lineEnd))
}

fun Document.charAt(offset: Int): Char? {
    if (textLength <= offset)
        return null
    return getText(TextRange.create(offset, offset+1))[0]
}