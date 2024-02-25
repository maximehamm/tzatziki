package io.nimbly.i18n.util

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.nimbly.i18n.translation.EFormat
import org.apache.commons.text.StringEscapeUtils
import org.unbescape.properties.PropertiesEscape

fun Editor.detectFormat()
    = this.file?.detectFormat() ?: EFormat.SIMPLE

fun PsiFile.detectFormat(): EFormat {
    var lang = this.language.id.uppercase()
    if (lang == "TEXT") {
        lang = this.virtualFile.name.substringAfterLast(".").uppercase()
    }
    return when (lang) {
        "HTML", "HTM", "XHTML" -> EFormat.HTML
        "XML" -> EFormat.XML
        "CSV" -> EFormat.CSV
        "JSON" -> EFormat.JSON
        "PROPERTIES" -> EFormat.PROPERTIES
        else -> EFormat.SIMPLE
    }
}

fun String.unescapeFormat(format: EFormat): String {
    return when (format) {
        EFormat.HTML ->
            StringEscapeUtils.unescapeHtml4(this)
        EFormat.CSV ->
            StringEscapeUtils.unescapeCsv(this)
        EFormat.XML ->
            StringEscapeUtils.unescapeXml(this)
        EFormat.JSON ->
            StringEscapeUtils.unescapeJson(this)
        EFormat.PROPERTIES ->
            PropertiesEscape.unescapeProperties(this)
        else ->
            this
    }
}

fun String.escapeFormat(format: EFormat): String {
    return when (format) {
        EFormat.HTML ->
            StringEscapeUtils.unescapeHtml4(this)
        EFormat.CSV ->
            StringEscapeUtils.escapeCsv(this)
        EFormat.XML ->
            StringEscapeUtils.escapeXml11(this)
        EFormat.JSON ->
            StringEscapeUtils.escapeJson(this)
        EFormat.PROPERTIES ->
            PropertiesEscape.escapePropertiesValue(this)
        else ->
            this
    }
}

/**
 * unicodeEscape
 *
 * @param this@unicodeEscape the s
 * @return the string
 */
fun String.unicodeEscape(): String {
    val sb = StringBuilder()
    for (i in this.indices) {
        val c = this[i]
        if ((c.code shr 7) > 0) {
            sb.append("\\u")
            sb.append(hexChar[c.code shr 12 and 0xF]) // append the hex character for the left-most 4-bits
            sb.append(hexChar[c.code shr 8 and 0xF]) // hex for the second group of 4-bits from the left
            sb.append(hexChar[c.code shr 4 and 0xF]) // hex for the third group
            sb.append(hexChar[c.code and 0xF]) // hex for the last group, e.g., the right most 4-bits
        } else if (c == '\n' || c == '\r') {
            sb.append('\\').append(if (c == '\n') 'n' else 'r')
            if (i < length - 1 && this[i + 1] != ' ') sb.append(c)
        } else if (c == '\t') {
            sb.append("\\t")
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}

private val hexChar = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

