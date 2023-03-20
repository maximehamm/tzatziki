package io.nimbly.tzatziki.reference

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.java.steps.reference.CucumberJavaParameterTypeReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReference.EMPTY_ARRAY
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class CucumberKotlinReferenceProvider : PsiReferenceProvider() {
    override fun acceptsTarget(target: PsiElement): Boolean {
        return true
    }

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {

        if (element !is KtStringTemplateExpression)
            return EMPTY_ARRAY

        val literalTemplate = element.entries.firstOrNull() as? KtLiteralStringTemplateEntry
            ?: return EMPTY_ARRAY

        val annotationEntry = PsiTreeUtil.getParentOfType(element, KtAnnotationEntry::class.java)
            ?: return EMPTY_ARRAY

        if (annotationEntry.resolveToDescriptorIfAny()?.fqName?.asString()?.startsWith("io.cucumber.java") != true)
            return EMPTY_ARRAY

        val offset = literalTemplate.textRange.startOffset - element.textRange.startOffset
        val result = mutableListOf<PsiReference>()
        CucumberUtil.processParameterTypesInCucumberExpression(literalTemplate.text) {

            val range = it.shiftRight(offset)
            result.add( CucumberJavaParameterTypeReference(element, range));
        }

        return result.toTypedArray()
    }
}
