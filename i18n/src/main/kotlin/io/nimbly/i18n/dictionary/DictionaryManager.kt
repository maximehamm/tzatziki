package io.nimbly.i18n.dictionary

import io.nimbly.i18n.util.fromCamelCase

object DictionaryManager {

    private val listeners: MutableList<DictionaryListener> = mutableListOf()

    fun registerListener(listener: DictionaryListener) {
        listeners.add(listener)
    }

    fun searchDefinition(
        text: String,
        camelCase: Boolean = false,
    ): DefinitionResult {

        val translationText = if (camelCase) text.fromCamelCase() else text

        val def = io.nimbly.i18n.dictionary.searchDefinition(translationText)

        val event = DictionaryEvent(def)
        listeners.forEach { it.onDefinition(event) }

        return def
    }
}

interface DictionaryListener {
    fun onDefinition(event: DictionaryEvent)
}

class DictionaryEvent(val definition: DefinitionResult)
