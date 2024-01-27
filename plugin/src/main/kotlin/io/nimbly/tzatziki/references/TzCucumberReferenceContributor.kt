/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.cucumber.psi.GherkinElementTypes
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepImpl

class TzCucumberReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(GherkinStepImpl::class.java), TzCucumberStepReferenceProvider()
        )
    }
}

class TzCucumberStepReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {

        if (element is GherkinStepImpl) {

            var node = element.node.findChildByType(TEXT_AND_PARAM_SET)
            if (node != null) {

                val start = node.textRange.startOffset
                var end = node.textRange.endOffset
                var endBeforeSpace = end
                node = node.treeNext

                while (node != null && TEXT_PARAM_AND_WHITE_SPACE_SET.contains(node.elementType)) {
                    endBeforeSpace = if (node.elementType === TokenType.WHITE_SPACE) end else node.textRange.endOffset
                    end = node.textRange.endOffset
                    node = node.treeNext
                }

                val textRange = TextRange(start, endBeforeSpace)
                val reference = TzCucumberStepReference(element, textRange.shiftRight(-element.getTextOffset()))

                return arrayOf(reference)
            }
        }

        return PsiReference.EMPTY_ARRAY
    }

    companion object {

        private val TEXT_AND_PARAM_SET = TokenSet.create(
            GherkinTokenTypes.TEXT,
            GherkinTokenTypes.STEP_PARAMETER_TEXT,
            GherkinTokenTypes.STEP_PARAMETER_BRACE,
            GherkinElementTypes.STEP_PARAMETER
        )
        private val TEXT_PARAM_AND_WHITE_SPACE_SET: TokenSet = TokenSet.orSet(
            TEXT_AND_PARAM_SET,
            TokenSet.WHITE_SPACE)

    }
}