package io.nimbly.i18n.translation.engines.google

import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.Translation

class GoogleEngine(override val type: EEngine) : IEngine {

    override fun translate(targetLanguage: String, sourceLanguage: String, textToTranslate: String): Translation? {
        TODO("Not yet implemented")
    }

    override fun documentation() = """<html>
        See documentation <a href='DOC'>here</a>.
    </html>""".trimIndent()

    override fun label() = "Google Translate API"

    override fun needApiKey() = true

    override fun languages(): Map<String, String> {
        TODO("Not yet implemented")
    }
}