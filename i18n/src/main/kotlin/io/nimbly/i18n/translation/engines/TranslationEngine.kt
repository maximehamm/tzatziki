package io.nimbly.i18n.translation.engines

import io.nimbly.i18n.translation.engines.google.DeepLEngine
import io.nimbly.i18n.translation.engines.google.GoogleEngine
import java.io.IOException

enum class EEngine { GOOGLE, DEEPL }

object TranslationEngineFactory {

    private val engines = mapOf(
        EEngine.GOOGLE to GoogleEngine(),
        EEngine.DEEPL to DeepLEngine())

    fun engines()
        = engines

    fun engine(id: EEngine)
        = engines[id]!!
}

interface IEngine {

    /**
     * Translate
     *
     * @param key               the key
     * @param targetLanguage    the target language
     * @param sourceLanguage    the source language
     * @param textToTranslate the source translation
     * @return the string
     * @throws IOException the io exception
     */
    fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String): Translation?
}

data class Translation(
    var translated: String,
    val sourceLanguageIndentified: String
)
