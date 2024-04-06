package io.nimbly.i18n.translation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.Translation
import io.nimbly.i18n.translation.engines.TranslationEngineFactory
import io.nimbly.i18n.util.*
import java.util.*
import javax.swing.SwingUtilities

object TranslationManager {

    private val listeners: MutableList<TranslationListener> = mutableListOf()
    private var findUsages: Set<Pair<PsiElement, Int>>? = null

    fun registerListener(listener: TranslationListener) {
        listeners.add(listener)
    }

    fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        text: String,
        format: EFormat,
        style: EStyle,
        origin: Origin?,
        project: Project?
    ): Translation? {

        val t = text.unescapeStyle(style)
        val translationText = t.unescapeFormat(format, false)

        val mySettings = TranslationPlusSettings.getSettings()
        val activeEngine = mySettings.activeEngine

        val engine = TranslationEngineFactory.engine(activeEngine)

        val translation =
            if (format.preserveQuotes && translationText.surroundedWith("\n"))
                engine.translate(targetLanguage, sourceLanguage, translationText.removeSurrounding("\""))
                    ?.apply { this.translated = this.translated.surround("\"")}
            else
                engine.translate(targetLanguage, sourceLanguage, translationText)


        if (translation != null) {

            translation.translated =

                translation.translated
                    .replace("„", "\"")
                    .postTranslation(format)
                    .escapeStyle(style, Locale(sourceLanguage))

            val event = TranslationEvent(translation)

            updateListenersAfterTranslation(project, event)
        }

        if (origin?.element != null && origin.editor != null) {
            findUsages = null
            updateListenersAfterUsagesCollected(origin.element, project)
            SwingUtilities.invokeLater {
                findUsages = findUsages(origin.element, origin.editor, GlobalSearchScope.allScope(origin.element.project))
                updateListenersAfterUsagesCollected(origin.element, project)
            }
        }
        else {
            findUsages = null
            updateListenersAfterUsagesCollected(origin?.element, project)
        }


        return translation
    }

    private fun updateListenersAfterTranslation(project: Project?, event: TranslationEvent) {

        if (project != null) {
            DumbService.getInstance(project).smartInvokeLater {
                listeners.forEach { it.onTranslation(event) }
            }
        } else {
            listeners.forEach { it.onTranslation(event) }
        }
    }

    private fun updateListenersAfterUsagesCollected(origin: PsiElement?, project: Project?) {

        val usages = getUsages()
        if (project != null && origin != null) {
            DumbService.getInstance(project).smartInvokeLater {
                listeners.forEach { it.onUsagesCollected(origin, usages) }
            }
        } else {
            listeners.forEach { it.onUsagesCollected(null, usages) }
        }
    }

    fun getUsages(): Set<PsiElement> {
        return findUsages?.map { it.first }?.toSet() ?: emptySet()
    }

    fun changeEngine(engine: EEngine) {
        listeners.forEach { it.onEngineChanged(engine) }
    }
}
interface TranslationListener {
    fun onTranslation(event: TranslationEvent)
    fun onUsagesCollected(origin: PsiElement?, usages: Set<PsiElement>)
    fun onEngineChanged(engine: EEngine)
}

class TranslationEvent(
    val translation: Translation
)

class Origin(
    val element: PsiElement?,
    val editor: Editor?,
) {
    companion object {
        fun from(element: PsiElement?, editor: Editor?): Origin? {
            if (element == null && editor == null)
                return null
            return Origin(element, editor)
        }
    }
}
