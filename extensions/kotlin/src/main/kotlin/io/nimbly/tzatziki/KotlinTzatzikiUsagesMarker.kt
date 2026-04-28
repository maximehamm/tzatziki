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

package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.usages.TzStepsUsagesMarker
import io.nimbly.tzatziki.util.findUsages
import io.nimbly.tzatziki.util.up
import org.jetbrains.kotlin.psi.*

class KotlinTzatzikiUsagesMarker : TzStepsUsagesMarker() {

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        elements
            .filterIsInstance<LeafPsiElement>()
            .forEach { token ->

                // Check context — find the enclosing annotation entry then the named function.
                val literal = token.parent as? KtLiteralStringTemplateEntry ?: return@forEach
                val template = literal.parent as? KtStringTemplateExpression ?: return@forEach
                val argument = template.parent as? KtValueArgument ?: return@forEach
                argument.parent as? KtValueArgumentList ?: return@forEach
                val annotationEntry = PsiTreeUtil.getParentOfType(argument, KtAnnotationEntry::class.java) ?: return@forEach
                val namedFunction = PsiTreeUtil.getParentOfType(annotationEntry, KtNamedFunction::class.java) ?: return@forEach

                // Check class is using cucumber
                // Not the best way, but I didn't find how to check which class the named function is using...
                val clazz = PsiTreeUtil.getParentOfType(token, KtClass::class.java) ?: return@forEach
                clazz.containingKtFile.importList?.imports
                    ?.map { it.importPath }
                    ?.find { it?.fqName?.asString()?.startsWith("io.cucumber.") == true }
                    ?: return@forEach

                // Get annotation text
                DumbService.getInstance(token.project).runReadActionInSmartMode {

                    val annotationText = token.text
                    val usages = findUsages(namedFunction)

                    // Build markers
                    buildMarkers(token, usages, annotationText, result)
                }
            }
    }
}
