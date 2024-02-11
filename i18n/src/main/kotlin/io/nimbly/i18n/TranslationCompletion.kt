package io.nimbly.i18n

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy.NEVER_AUTOCOMPLETE
import com.intellij.ide.util.PropertiesComponent
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import icons.ActionI18nIcons
import io.nimbly.i18n.util.SAVE_OUTPUT
import io.nimbly.i18n.util.TranslationIcons
import io.nimbly.i18n.util.safeText

class TranslationCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val origin = parameters.position
        val startOffset = origin.textRange.startOffset
        val endOffset = origin.textRange.endOffset
        val text = origin.text.safeText

        val output = PropertiesComponent.getInstance().getValue(SAVE_OUTPUT, "EN")
        val icon =  TranslationIcons.getFlag(output.trim().lowercase()) ?: ActionI18nIcons.I18N

        val lookup = LookupElementBuilder.create(text + "X")
            .withPsiElement(origin.navigationElement)
            .withPresentableText("Translate")
            .withIcon(icon)
            .withTailText("Translation+")
            .appendTailText(" Hop", true)
            .bold()
//            .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
//
//                override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
//
//                    presentation.icon = ActionI18nIcons.TRANSLATION_PLUS_16
//                    presentation.itemText = "TEST 1"
//
//                    presentation.typeText = "TEST 2"
//                }
//            })
            .withAutoCompletionPolicy(NEVER_AUTOCOMPLETE)

        resultSet.addElement(
            PrioritizedLookupElement.withPriority(lookup, 100.0))

    }

    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet)
                        = complete(parameters, context, resultSet)
            }
        )
    }
}