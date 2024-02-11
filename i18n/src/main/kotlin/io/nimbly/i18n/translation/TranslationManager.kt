package io.nimbly.i18n.translation

import io.nimbly.i18n.util.fromCamelCase
import io.nimbly.i18n.util.toCamelCase
import java.util.*

object TranslationManager {

    private val listeners: MutableList<TranslationListener> = mutableListOf()

    fun registerListener(listener: TranslationListener) {
        listeners.add(listener)
    }

    fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        text: String,
        camelCase: Boolean = false,
    ): GTranslation? {

        val translationText = if (camelCase) text.fromCamelCase() else text

        val translation = googleTranslate(targetLanguage, sourceLanguage, translationText)
        if (translation != null) {

            translation.translated = translation.translated.replace("„", "\"")

            if (camelCase) {
                translation.translated = translation.translated
                    .replace("'", " ")
                    .replace("-", " ")
                    .toCamelCase(locale = Locale(sourceLanguage))
            }

            val event = TranslationEvent(translation)
            listeners.forEach { it.onTranslation(event) }
        }

        return translation
    }
}

interface TranslationListener {
    fun onTranslation(event: TranslationEvent)
}

class TranslationEvent(val translation: GTranslation)
