package io.nimbly.i18n.util

import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.meta.PsiWritableMetaData
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor
import io.nimbly.i18n.translation.REFACTORING
import io.nimbly.i18n.translation.REFACTORING_PREVIEW
import io.nimbly.i18n.translation.REFACTORING_SEARCH_IN_COMMENT

fun canRename(element: PsiElement?): Boolean {

    element ?: return false

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
    var useRefactoring = PropertiesComponent.getInstance().getValue(REFACTORING) == "true"
        set(value) {
            field = value
            PropertiesComponent.getInstance().setValue(REFACTORING, value.toString())
        }
    var preview = PropertiesComponent.getInstance().getValue(REFACTORING_PREVIEW) == "true"
        set(value) {
            field = value
            PropertiesComponent.getInstance().setValue(REFACTORING_PREVIEW, value)
        }
    var searchInComments: Boolean = PropertiesComponent.getInstance().getValue(REFACTORING_SEARCH_IN_COMMENT) == "true"
        set(value) {
            field = value
            PropertiesComponent.getInstance().setValue(REFACTORING_SEARCH_IN_COMMENT, value)
        }
}
