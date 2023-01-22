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

package io.nimbly.tzatziki.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import freemarker.template.Configuration
import freemarker.template.Template
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter

class Path(val path : String)

val PAGE_COUNTER = "'Page ' counter(page) ' / ' counter(pages);"

fun buildPdf(generator: PdfBuilder, outputStream: OutputStream) {

    val generated = generator.generate()
        .replace(Regex("<\\?(XML).+?>", option = RegexOption.IGNORE_CASE), "");

    PdfRendererBuilder().apply {
        useFastMode()
        useSVGDrawer(BatikSVGDrawer())
        useFont(File(generator.getFont().path), "cucumberplus")
        withHtmlContent(generated, "..")
        toStream(outputStream)
        run()
    }
}

fun String.escape(): String {
    val out = StringBuilder(16.coerceAtLeast(this.length))
    for (element in this) {
        val c = element
        if (c == '"') {
            out.append("&quot;")
        }
        else if (c == '\'' || c == '<' || c == '>' || c == '&') {
            out.append("&#")
            out.append(c.toInt())
            out.append(';')
        }
        else if (c == '\n') {
            out.append("<br/>")
        }
        else {
            out.append(c)
        }
    }
    return out.toString()
}

fun PdfBuilder.append(templateName: String, config: Configuration, vararg data: Pair<String, Any?>) {
    append(templateName, data.toMap(), config)
}

fun PdfBuilder.append(templateName: String, data: Any, config: Configuration) {
    val merged = mergeTemplate(templateName, data, config)
    this.append(merged)
}

fun mergeTemplate(template: Path, data: Any, config: Configuration): String
    = mergeTemplate(template.path, data, config)


fun mergeTemplate(templateName: String, data: Any, config: Configuration): String {

    val temp: Template = config.getTemplate(templateName)
    val aos = ByteArrayOutputStream()
    val out = OutputStreamWriter(aos)
    temp.process(data, out)
    return  aos.toString("UTF-8")
}