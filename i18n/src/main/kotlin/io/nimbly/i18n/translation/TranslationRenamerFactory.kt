package io.nimbly.i18n.translation

import com.intellij.ide.util.PropertiesComponent
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import io.nimbly.i18n.util.detectStyle
import kotlinx.coroutines.*


class TranslationRenamerFactory : NameSuggestionProvider {

    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>
    ): SuggestedNameInfo? {

        val input = PropertiesComponent.getInstance().getValue(SAVE_INPUT, Lang.AUTO.code)
        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, Lang.DEFAULT.code)

        if (nameSuggestionContext is PsiNameIdentifierOwner) {

            val name = nameSuggestionContext.nameIdentifier?.text
                ?: return null

            val deferred: Deferred<GTranslation?> = GlobalScope.async(Dispatchers.Default) {
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