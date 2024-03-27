package io.nimbly.tzatziki

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.nimbly.tzatziki.util.TzStepTypesCompletion
import io.nimbly.tzatziki.util.getLineStart
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry

/**
 * Complete step definition
 *  - Standard types
 *  - Custom types
 *  @see <a href="https://github.com/cucumber/cucumber-expressions#readme">cucumber-expressions</a>
 */
class KotlinStepTypesCompletion : TzStepTypesCompletion() {

    override fun complete(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet) {

        val psiElement = parameters.position.parent
        if (psiElement !is KtStringTemplateEntry)
            return

        val project = psiElement.project
        val annotation = PsiTreeUtil.getParentOfType(psiElement, KtAnnotationEntry::class.java)
            ?: return
        if (annotation.resolveToDescriptorIfAny()?.fqName?.asString()?.startsWith("io.cucumber.java") != true)
            return

        val document = parameters.editor.document
        val lineStart = document.getLineStart(parameters.offset)
        val text = document.getText(TextRange(lineStart, parameters.offset))

        if (!text.matches(".*\\{(\\w|\\s|\\d)*$".toRegex()))
            return

        //
        // Custom types
        val projectScope = GlobalSearchScope.allScope(project)
        val parameterTypeClass = JavaPsiFacade.getInstance(project).findClass("io.cucumber.java.ParameterType", projectScope)
        if (parameterTypeClass != null) {

            AnnotatedElementsSearch.searchPsiMethods(parameterTypeClass, projectScope).forEach { psiMethod ->
                val lookup = LookupElementBuilder.create(psiMethod.name)
                    .withPresentableText("{" + psiMethod.name + "}")
                    .withIcon(psiMethod.getIcon(0))
                    .withTailText("Convert to " + (psiMethod.returnType?.presentableText ?: "Void"))
                    .appendTailText(psiMethod.containingClass?.name?.let { ", defined in class $it" } ?: "", false)
                    .bold()
                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
            }
        }

        //
        // Standard types
        standardTypesLookup(resultSet)
    }
}
