package io.nimbly.i18n.util

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.nimbly.i18n.translation.EFormat
import org.apache.commons.text.StringEscapeUtils
import org.unbescape.properties.PropertiesEscape

fun Editor.detectFormat()
    = this.file?.detectFormat() ?: EFormat.TEXT

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
        else -> EFormat.TEXT
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
    return preserveQuotes(format) {
        when (format) {
            EFormat.HTML ->
                StringEscapeUtils.unescapeHtml4(it)
            EFormat.CSV ->
                StringEscapeUtils.escapeCsv(it)
            EFormat.XML ->
                StringEscapeUtils.escapeXml11(it)
            EFormat.JSON ->
                StringEscapeUtils.escapeJson(it)
            EFormat.PROPERTIES ->
                PropertiesEscape.escapePropertiesValue(it)
            else ->
                this
        }
    }
}

fun String.surroundedWith(s: String)
        = this.startsWith("\"") && this.endsWith("\"")

fun String.surround(s: String)
        = s + this + s

private fun String.preserveQuotes(format: EFormat, function: (s: String) -> String) =
    if (format.preserveQuotes && this.startsWith("\"") && this.endsWith("\"")) {
        function(this.removeSurrounding("\"")).surround("\"")
    }
    else {
        function(this)
    }