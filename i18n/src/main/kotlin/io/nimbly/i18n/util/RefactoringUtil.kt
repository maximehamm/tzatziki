package io.nimbly.i18n.util

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.meta.PsiWritableMetaData
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor
import com.intellij.refactoring.suggested.startOffset
import com.intellij.refactoring.util.NonCodeUsageInfo
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
    origin: PsiElement?,
    editor: Editor,
): Set<Pair<PsiElement, Int>> {

    val targets = mutableSetOf<Pair<PsiElement, Int>>()
    origin ?: return targets

    var el: PsiElement = if (origin is LeafPsiElement) origin.parent else origin
    val ref = el.reference
    if (ref != null) {
        el = ref.resolve() ?: return emptySet()

        val o = (el as? PsiNameIdentifierOwner)?.identifyingElement?.startOffset ?: el.startOffset
        targets.add(el to o)
    }

//    val query: Query<PsiReference> = ReferencesSearch.search(el, GlobalSearchScope.projectScope(el.project))
//    query.forEach { psiReference ->
//        val element: PsiElement = psiReference.element
//        if (psiReference != ref)
//            targets.add(element to element.startOffset)
//    }
//
//    return targets

    val refactoringSetup = RefactoringSetup()

    val elt = RenamePsiElementProcessor.forElement(el).substituteElementToRename(el, editor)?.findRenamable() ?: el

    val scope = GlobalSearchScope.projectScope(el.project)
    val rename = RefactoringFactory.getInstance(elt.project)
        .createRename(elt, "xx", scope, refactoringSetup.searchInComments, true)
    val usages = rename.findUsages()

    val allRenames = mutableMapOf<PsiElement, String>()
    allRenames[elt] = "xxx"

    val processors = RenamePsiElementProcessor.allForElement(elt)
    for (processor in processors) {
        if (processor.canProcessElement(elt)) {
            processor.prepareRenaming(elt, "xxx", allRenames)
        }
    }

    allRenames.forEach { (resolved, renamed) ->
        if (resolved is PsiNameIdentifierOwner) {
            val o = resolved.identifyingElement?.startOffset
            if (o != null && o != elt.startOffset)
                targets.add(resolved.identifyingElement!! to o)
        }
    }

    usages.forEach { usage ->
        val virtualFile = usage.element?.containingFile?.virtualFile
        if (usage is NonCodeUsageInfo && virtualFile!=null && scope.contains(virtualFile)) {
            val o = (usage.element?.startOffset ?: -1) + (usage.rangeInElement?.startOffset ?: -1)
            if (o >= 0)
                targets.add(usage.element!! to o)
        } else {
            val r = usage.reference?.element
            val vf = r?.containingFile?.virtualFile
            if (r != null && vf!=null && scope.contains(vf)) {
                PsiTreeUtil.collectElements(r) {
                    if (it is PsiReference && it.resolve() == elt)
                        targets.add( it to it.startOffset + it.rangeInElement.startOffset)
                    false
                }
            }
        }
    }

    // Remove selected item
    targets.removeIf { it.first.containingFile == origin.containingFile && origin.textRange.contains(it.second) }

    // Remove duplicate items
    return targets
        .groupBy { it.first.containingFile to it.second }
        .map { it.value.first() }
        .toSet()
}

