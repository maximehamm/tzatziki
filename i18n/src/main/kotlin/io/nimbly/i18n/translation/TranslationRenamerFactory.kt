package io.nimbly.i18n.translation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.Translation
import io.nimbly.i18n.util.EFormat
import io.nimbly.i18n.util.detectStyle
import kotlinx.coroutines.*

class TranslationRenamerFactory : NameSuggestionProvider {

    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>
    ): SuggestedNameInfo? {

        val settings = TranslationPlusSettings.getSettings()
        val input = settings.input
        val output = settings.output

        if (nameSuggestionContext is PsiNameIdentifierOwner) {

            val name = nameSuggestionContext.nameIdentifier?.text
                ?: return null

            val deferred: Deferred<Translation?> = GlobalScope.async(Dispatchers.Default) {
                val translation = TranslationManager.translate(output, input, name, EFormat.TEXT, name.detectStyle(true), null, element.project)
                translation;
            }

            runBlocking {

                val gTranslation = withTimeoutOrNull(250L) {
                    deferred.await()
                }

                if (gTranslation != null && name != gTranslation.translated)
                    result.add(gTranslation.translated)

            }
        }

        return null
    }


}