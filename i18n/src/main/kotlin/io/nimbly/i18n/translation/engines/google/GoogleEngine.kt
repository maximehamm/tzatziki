package io.nimbly.i18n.translation.engines.google

import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.Translation

class GoogleEngine(override val type: EEngine) : IEngine {

    override fun translate(targetLanguage: String, sourceLanguage: String, textToTranslate: String): Translation? {
        TODO("Not yet implemented")
    }

    override fun label() = "Google Translate API"

    override fun needApiKey() = true
}