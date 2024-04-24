package io.nimbly.i18n.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.searches.ReferencesSearch

fun PsiElement?.findRenamable(): PsiElement? {

    var elt = this
    if (elt is LeafPsiElement)
        elt = elt.parent
    if (elt?.reference != null)
        elt = elt.reference?.resolve()

//    if (elt !is PsiNamedElement) {
//        elt = elt?.parent
//    }
//    if (elt is PsiReference) {
//        elt = elt.resolve()
//    }
    return elt
}

