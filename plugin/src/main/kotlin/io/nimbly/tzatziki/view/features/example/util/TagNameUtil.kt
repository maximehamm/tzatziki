package io.nimbly.tzatziki.view.features.example.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import java.util.stream.Collectors

/**
 * Utilities for retrieving Gherkin tag and Story meta names.
 */
object TagNameUtil {
    /**
     * Returns the argument Gherkin tag's name without the leading @ symbol.
     */
    fun tagNameFrom(tag: GherkinTag): String {
        return tag.name.substring(1)
    }

    fun metaNameFrom(metaKeyElement: PsiElement, metaTextElement: Collection<PsiElement>?): String {
        return if (metaTextElement != null && !metaTextElement.isEmpty()) metaKeyElement.text.substring(1) + ":" + metaTextElement.stream()
            .map { obj: PsiElement -> obj.text }
            .collect(Collectors.joining(" ")) else metaKeyElement.text.substring(1)
    }

    @JvmStatic
    fun determineTagOrMetaName(element: PsiElement): String {
        if (element is GherkinTag) {
            return tagNameFrom(element)
        }
        return element.firstChild.text
    }
}
