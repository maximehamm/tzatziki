package io.nimbly.i18n

import io.nimbly.i18n.util.GTranslation
import io.nimbly.i18n.util.googleTranslate

object TranslationManager {

    private val listeners: MutableList<TranslationListener> = mutableListOf()

    fun registerListener(listener: TranslationListener) {
        listeners.add(listener)
    }

    fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        sourceTranslation: String
    ): GTranslation? {


        val translation = googleTranslate(targetLanguage, sourceLanguage, sourceTranslation)
        if (translation != null) {

            translation.translated = translation.translated.replace("„", "\"")

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
