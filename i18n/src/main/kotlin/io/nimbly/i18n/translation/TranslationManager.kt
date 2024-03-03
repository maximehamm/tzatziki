package io.nimbly.i18n.translation

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import io.nimbly.i18n.util.*
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
        format: EFormat,
        style: EStyle,
        project: Project
    ): GTranslation? {

        val t = text.unescapeStyle(style)
        val translationText = t.unescapeFormat(format, false)

        val translation =
            if (format.preserveQuotes && translationText.surroundedWith("\n"))
                googleTranslate(targetLanguage, sourceLanguage, translationText.removeSurrounding("\""))
                    ?.apply { this.translated = this.translated.surround("\"")}
            else
                googleTranslate(targetLanguage, sourceLanguage, translationText)


        if (translation != null) {

            translation.translated =

                translation.translated
                    .replace("„", "\"")
                    .postTranslation(format)
                    .escapeStyle(style, Locale(sourceLanguage))

            val event = TranslationEvent(translation)

            DumbService.getInstance(project).smartInvokeLater {
                listeners.forEach { it.onTranslation(event) }
            }
        }

        return translation
    }
}

interface TranslationListener {
    fun onTranslation(event: TranslationEvent)
}

class TranslationEvent(val translation: GTranslation)