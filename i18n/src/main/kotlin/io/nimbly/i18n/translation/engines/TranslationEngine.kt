package io.nimbly.i18n.translation.engines

import io.nimbly.i18n.translation.engines.EEngine.*
import io.nimbly.i18n.translation.engines.google.DeepLEngine
import io.nimbly.i18n.translation.engines.google.GoogleEngine
import io.nimbly.i18n.translation.engines.google.GoogleEngineFree
import java.io.IOException

enum class EEngine { GOOGLE_FREE, GOOGLE, DEEPL }

object TranslationEngineFactory {

    private val engines = listOf(
        GoogleEngineFree(GOOGLE_FREE),
        GoogleEngine(GOOGLE),
        DeepLEngine(DEEPL))

    fun engines()
        = engines

    fun engine(id: EEngine)
        = engines.find { it.type == id }!!
}

interface IEngine {

    val type: EEngine

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

    fun label(): String

    fun needApiKey() : Boolean
}

data class Translation(
    var translated: String,
    val sourceLanguageIndentified: String
)
