package io.nimbly.i18n.util

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.nimbly.i18n.translation.EFormat
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
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

fun String.unescapeFormat(format: EFormat, displayOnly: Boolean): String {
    return try {
        when (format) {
            EFormat.HTML ->
                StringEscapeUtils.unescapeHtml4(this)
            EFormat.CSV ->
                this.unescapeCSV(displayOnly)
            EFormat.XML ->
                StringEscapeUtils.unescapeXml(this)
            EFormat.JSON ->
                StringEscapeUtils.unescapeJson(this)
            EFormat.PROPERTIES ->
                PropertiesEscape.unescapeProperties(this)
            else ->
                this
        }
    } catch (e: Exception) {
        return this
    }
}

fun String.escapeFormat(format: EFormat): String {
    return try {
        preserveQuotes(format) {
            when (format) {
                EFormat.HTML ->
                    StringEscapeUtils.unescapeHtml4(it)
                EFormat.CSV ->
                    this.escapeCSV()
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
    } catch (e: Exception) {
        return this
    }
}

private fun String.unescapeCSV(displayOnly: Boolean): String {

    fun unescape(s: String): String {
        return try {
            StringEscapeUtils.unescapeCsv(s)
        } catch (e: Exception) {
            s
        }
    }

    if (!displayOnly) {
        try {
            val parsed = CSVParser.parse(this, CSVFormat.DEFAULT)
            val sb = StringBuilder()
            parsed.records.forEach { record ->
                record.forEachIndexed { i, col ->
                    sb.append(unescape(col.trim()))
                    if (i != record.size() - 1)
                        sb.append(" [#] ")
                }
                sb.append("\n")
            }
            return sb.toString().trim()
        } catch (ignored: Exception) {
        }
    }
    return unescape(this)
}

private fun String.escapeCSV(): String {
    try {
        val parsed = CSVParser.parse(this, CSVFormat.DEFAULT)
        val sb = StringBuilder()
        parsed.records.forEach { record ->
            record.forEachIndexed { i, col ->
                sb.append(StringEscapeUtils.escapeCsv(col.trim()))
                if (i != record.size() - 1)
                    sb.append(",")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    } catch (ignored: Exception) {
    }
    return StringEscapeUtils.escapeCsv(this)
}

fun String.postTranslation(format: EFormat): String {
    return when (format) {
        EFormat.CSV ->
            this.postTranslationCSV()
        else ->
            this
    }
}

private fun String.postTranslationCSV(): String {
    try {
        val sb = StringBuilder()
        this.split("\n").forEach { line ->
            val split = line.split("[#]")
            split.forEachIndexed { i, col ->
                sb.append(StringEscapeUtils.escapeCsv(col.trim()))
                if (i != split.size - 1)
                    sb.append(",")
            }
            sb.append("\n")
        }
        return sb.trim().toString()
    } catch (ignored: Exception) {
    }
    return this
}

fun String.surroundedWith(s: String)
        = this.startsWith("\"") && this.endsWith("\"")

fun String.surround(s: String)
        = s + this + s

fun String.removeQuotes()
        = this.removeSurrounding("\"").removeSurrounding("'")

fun String.preserveQuotes(format: EFormat? = null, function: (s: String) -> String) =
    if ((format == null || format.preserveQuotes) && this.startsWith("\"") && this.endsWith("\"")) {
        function(this.removeSurrounding("\"")).surround("\"")
    }
    else {
        function(this)
    }
