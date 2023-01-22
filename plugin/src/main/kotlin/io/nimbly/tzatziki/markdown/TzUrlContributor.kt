/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.markdown

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl

class TzUrlContributor : PsiReferenceContributor() {

    fun loadReferences(element: PsiElement): List<PsiReference> {

        if (element !is GherkinFeatureHeaderImpl)
            return emptyList()

        println("## ${element.text}")
        val text = element.text

        return emptyList()
    }


    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    return loadReferences(element).toTypedArray()
                }

                override fun acceptsHints(element: PsiElement, hints: PsiReferenceService.Hints): Boolean {
                    return true
                }

                override fun acceptsTarget(target: PsiElement): Boolean {
                    return true
                }
            })
    }
}