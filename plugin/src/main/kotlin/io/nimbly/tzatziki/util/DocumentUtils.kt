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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

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