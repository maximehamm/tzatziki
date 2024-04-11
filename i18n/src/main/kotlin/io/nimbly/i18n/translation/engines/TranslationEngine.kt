package io.nimbly.i18n.translation.engines

import io.nimbly.i18n.translation.engines.EEngine.*
import io.nimbly.i18n.translation.engines.deepl.DeepLEngine
import io.nimbly.i18n.translation.engines.deepl.DeepLEnginePro
import io.nimbly.i18n.translation.engines.google.GoogleEngineFree
import io.nimbly.i18n.translation.engines.microsoft.DeepTranslate
import io.nimbly.i18n.translation.engines.microsoft.MicrosoftEngineFree
import io.nimbly.i18n.translation.engines.openai.ChatGPTEngine
import java.io.IOException

enum class EEngine { GOOGLE, DEEPL, MICROSOFT, DEEPL_PRO, CHATGPT, DEEP_TRANSLATE }

object TranslationEngineFactory {

    private val engines = listOf(
        GoogleEngineFree(),
        DeepLEngine(),
        //DeepLEnginePro(),
        MicrosoftEngineFree(),
        //ChatGPTEngine(),
        //DeepTranslate()
    )

    fun engines()
        = engines

    fun engine(id: EEngine)
        = engines.find { it.type == id } ?: engines.first()
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

    fun documentation(): String

    fun languages(): Map<String, String>
}

data class Translation(
    var translated: String,
    val sourceLanguageIndentified: String
)

class TranslationException(msg: String) : Exception(msg)
