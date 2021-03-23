package io.nimbly.tzatziki.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import java.io.IOException
import java.io.OutputStream


fun buildPdf(generator: PdfBuilder, outputStream: OutputStream) {

    val generated = generator.generate()
        .replace(Regex("<\\?(XML).+?>", option = RegexOption.IGNORE_CASE), "");

    PdfRendererBuilder().apply {
        useFastMode()
        useSVGDrawer(BatikSVGDrawer())
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