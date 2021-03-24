package io.nimbly.tzatziki.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import freemarker.template.Configuration
import freemarker.template.Template
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

class Path(val path : String)

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

fun getLogo() {

}

class Picture(
    val name: String,
    val content: String,
    val type: String
)