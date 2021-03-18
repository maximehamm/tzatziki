package io.nimbly.tzatziki.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

val PsiElement.previousPipe: PsiElement
    get() {
        var el = prevSibling
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                return el
            }
            el = el.prevSibling
        }
        throw Exception("Psi structure corrupted !")
    }

val PsiElement.nextPipe: PsiElement
    get() {
        var el = nextSibling
        while (el != null) {
            if (el is LeafPsiElement && el.elementType == GherkinTokenTypes.PIPE) {
                return el
            }
            el = el.nextSibling
        }
        throw Exception("Psi structure corrupted !")
    }