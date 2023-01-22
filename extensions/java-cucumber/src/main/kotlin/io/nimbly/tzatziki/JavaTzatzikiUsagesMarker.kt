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

package io.nimbly.tzatziki

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.usages.TzStepsUsagesMarker
import io.nimbly.tzatziki.util.findStepUsages

class JavaTzatzikiUsagesMarker : TzStepsUsagesMarker() {

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        elements
            .filterIsInstance<PsiJavaToken>()
            .forEach { token ->

                // Check context
                val annotation = PsiTreeUtil.getParentOfType(token, PsiAnnotation::class.java) ?: return@forEach
                if (annotation.resolveAnnotationType()?.qualifiedName?.startsWith("io.cucumber.java") != true) return@forEach
                val annotationText = (token.parent as? PsiLiteralExpression)?.value as? String ?: return@forEach
                val method = PsiTreeUtil.getParentOfType(token, PsiMethod::class.java) ?: return@forEach

                // Find method usages
                DumbService.getInstance(token.project).runReadActionInSmartMode {

                    val usages = findStepUsages(method)
                    if (usages.isNotEmpty())
                        buildMarkers(token, usages, annotationText, result)
                }
            }
    }
}