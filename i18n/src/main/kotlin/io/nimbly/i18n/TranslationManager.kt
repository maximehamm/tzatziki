package io.nimbly.i18n

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
    ): String? {


        val translation = googleTranslate(targetLanguage, sourceLanguage, sourceTranslation)

        val event = TranslationEvent(translation)
        listeners.forEach { it.onTranslation(event) }

        return translation
    }
}

interface TranslationListener {
    fun onTranslation(event: TranslationEvent)
}

class TranslationEvent(val translation: String?)
