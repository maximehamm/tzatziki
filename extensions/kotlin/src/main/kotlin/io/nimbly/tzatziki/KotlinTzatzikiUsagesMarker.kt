/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.nimbly.tzatziki.usages.TzStepsUsagesMarker
import io.nimbly.tzatziki.util.up
import org.jetbrains.kotlin.psi.*

class KotlinTzatzikiUsagesMarker : TzStepsUsagesMarker() {

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        elements
            .filterIsInstance<LeafPsiElement>()
            .forEach { token ->

                // Check context
                if (token.up(1) !is KtLiteralStringTemplateEntry) return@forEach
                if (token.up(2) !is KtStringTemplateExpression) return@forEach
                if (token.up(3) !is KtValueArgument) return@forEach
                if (token.up(4) !is KtValueArgumentList) return@forEach
                if (token.up(5) !is KtAnnotationEntry) return@forEach
                if (token.up(6) !is KtDeclarationModifierList) return@forEach
                val namedFunction = token.up(7) as? KtNamedFunction ?: return@forEach

                // Get annotation text
                val annotationText = token.text

                val usagesManager = (FindManager.getInstance(token.project) as FindManagerImpl).findUsagesManager
                val handler = usagesManager.getFindUsagesHandler(namedFunction, false) ?: return

                val usages = mutableListOf<PsiReference>()
                handler.processElementUsages(namedFunction, {
                    val ref = it.reference
                    if (ref !=null)
                        usages.add(ref)
                    true
                }, handler.findUsagesOptions)

                buildMarkers(token, usages, annotationText, result)
            }
    }
}