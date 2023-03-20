package io.nimbly.tzatziki.util

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

abstract class TzStepTypesCompletion : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet)
                        = complete(parameters, context, resultSet)
            }
        )
    }

    protected abstract fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet)

    protected fun standardTypesLookup(resultSet: CompletionResultSet) {
        STANDARD_TYPES.forEach() { type ->
            val lookup = LookupElementBuilder.create(type.first.trim())
                .withPresentableText("{" + type.first + "}")
                .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                .withTailText(type.second, true)
            resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 10.0))
        }
    }
}

val STANDARD_TYPES = listOf(
    "int" to "Integers, for example 71 or -19. Converts to a 32-bit signed integer if the platform supports it.",
    "float" to "Floats, for example 3.6, .8 or -9.2. Converts to a 32 bit float if the platform supports it.",
    "word" to "Words without whitespace, for example banana (but not banana split).",
    "string" to "Single-quoted or double-quoted strings, for example \"banana split\" or 'banana split' (but not banana split). Only the text between the quotes will be extracted. The quotes themselves are discarded. Empty pairs of quotes are valid and will be matched and passed to step code as empty strings.",
    "" to "Anonymous anything (/.*/).",
    "bigdecimal" to "Same as {float}, but converts to a BigDecimal if the platform supports it.",
    "double" to "Same as {float}, but converts to a 64 bit float if the platform supports it.",
    "biginteger" to "Same as {int}, but converts to a BigInteger if the platform supports it.",
    "byte" to "Same as {int}, but converts to an 8 bit signed integer if the platform supports it.",
    "short" to "Same as {int}, but converts to a 16 bit signed integer if the platform supports it.",
    "long" to "Same as {int}, but converts to a 64 bit signed integer if the platform supports it."
)