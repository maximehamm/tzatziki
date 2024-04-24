package io.nimbly.i18n.util

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.meta.PsiWritableMetaData
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Query
import io.nimbly.i18n.TranslationPlusSettings

fun canRename(element: PsiElement?): Boolean {

    element ?: return false
    element.parent ?: return false

    val hasRenameProcessor = RenamePsiElementProcessorBase.forPsiElement(element) !is DefaultRenamePsiElementProcessor
    val hasWritableMetaData = element is PsiMetaOwner && (element as PsiMetaOwner).metaData is PsiWritableMetaData

    if (!hasRenameProcessor && !hasWritableMetaData && element !is PsiNamedElement) {
        return false
    }

    if (!PsiManager.getInstance(element.project).isInProject(element)) {
        if (element.isPhysical) {
            val virtualFile = PsiUtilCore.getVirtualFile(element)
            if (!(virtualFile != null && NonProjectFileWritingAccessProvider.isWriteAccessAllowed( virtualFile, element.project))) {
                return false
            }
        }

        if (!element.isWritable) {
            return false
        }
    }

    if (isInInjectedLanguagePrefixSuffix(element)) {
        return false
    }

    return true
}

fun isInInjectedLanguagePrefixSuffix(element: PsiElement): Boolean {

    val injectedFile = element.containingFile ?: return false
    val project = injectedFile.project
    val languageManager = InjectedLanguageManager.getInstance(project)
    if (!languageManager.isInjectedFragment(injectedFile)) return false
    val elementRange = element.textRange
    val edibles = languageManager.intersectWithAllEditableFragments(injectedFile, elementRange)
    val combinedEdiblesLength = edibles.stream().mapToInt { obj: TextRange -> obj.length }.sum()

    return combinedEdiblesLength != elementRange.length
}

class RefactoringSetup {

    val settings = TranslationPlusSettings.getSettings()

    var useRefactoring = settings.useRefactoring
        set(value) {
            field = value
            settings.useRefactoring = value
        }
    var preview = settings.useRefactoringPreview
        set(value) {
            field = value
            settings.useRefactoringPreview = value
        }
    var searchInComments: Boolean = settings.useRefactoringSearchInComment
        set(value) {
            field = value
            settings.useRefactoringSearchInComment = value
        }
}

fun findUsages(
    origin: PsiElement?
): Set<Pair<PsiElement, Int>> {

    val results = mutableSetOf<Pair<PsiElement, Int>>()
    origin ?: return results

    var el: PsiElement = if (origin is LeafPsiElement) origin.parent else origin
    val ref = el.reference
    if (ref != null) {
        el = ref.resolve() ?: return emptySet()

        val o = (el as? PsiNameIdentifierOwner)?.identifyingElement?.startOffset ?: el.startOffset
        results.add(el to o)
    }

    val query: Query<PsiReference> = ReferencesSearch.search(el)
    query.forEach { psiReference ->
        val element: PsiElement = psiReference.element
        if (psiReference != ref)
            results.add(element to element.startOffset)
    }

    return results
}

