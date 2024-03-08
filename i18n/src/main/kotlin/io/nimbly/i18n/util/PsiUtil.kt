package io.nimbly.i18n.util

import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.CommonProcessors


fun PsiElement.findUsages(): List<PsiReference> {

    val usagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
    val handler = usagesManager.getFindUsagesHandler(this, false)
        ?: return emptyList()

    val usages = mutableListOf<PsiReference>()
    handler.processElementUsages(this, {
        val ref = it.reference
        if (ref != null)
            usages.add(ref)
        true
    }, handler.findUsagesOptions)
    return usages
}

fun PsiElement.collectElementsToRename(): Set<PsiElement> {
    val elementsToRename = HashSet<PsiElement>()

    // Add the element itself to be renamed
    elementsToRename.add(this)

    // Find all references to the given PsiElement
    ReferenceWalker(this).referencingElements.forEach { referencingElement ->
        elementsToRename.add(referencingElement)
    }

    return elementsToRename
}

private class ReferenceWalker(private val baseElement: PsiElement) : PsiRecursiveElementVisitor() {
    val referencingElements = HashSet<PsiElement>()

    init {
        baseElement.accept(this)
    }

    override fun visitElement(element: PsiElement) {
        if (element is PsiNameIdentifierOwner && element.text.isNotEmpty() && StringUtil.isNotEmpty(element.text) &&
            !element.textMatches(baseElement.text)) {

            ReferencesSearch.search(baseElement).forEach {
                referencingElements.add(it.element)
            }
        }
        super.visitElement(element)
    }
}

fun collectRefs(myElementToRename: PsiNamedElement, referencesSearchScope: SearchScope): Collection<PsiReference> {
    val search = ReferencesSearch.search(
        myElementToRename,
        referencesSearchScope, false
    )

    val processor: CommonProcessors.CollectProcessor<PsiReference> =
        object : CommonProcessors.CollectProcessor<PsiReference>() {
            override fun accept(reference: PsiReference): Boolean {
                ProgressManager.checkCanceled()
                return true
            }
        }

    search.forEach(processor)
    return processor.results
}

