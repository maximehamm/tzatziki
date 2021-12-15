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
import com.intellij.lang.javascript.psi.*
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.nimbly.tzatziki.usages.TzStepsUsagesMarker
import io.nimbly.tzatziki.util.findUsages
import io.nimbly.tzatziki.util.up
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptUtil

class JavascriptTzatzikiUsagesMarker : TzStepsUsagesMarker() {

    @Suppress("UNUSED_VARIABLE")
    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        elements
            .filterIsInstance<LeafPsiElement>()
            .forEach { token ->

                // Check context
                if (token.up(1) !is JSLiteralExpression) return@forEach
                if (token.up( 2) !is JSArgumentList) return@forEach
                val callExpression = (token.up(3) as? JSCallExpression) ?: return@forEach
                val ref = callExpression.methodExpression?.reference?.resolve() as? JSVariable ?: return@forEach

                val literal = callExpression.arguments.firstOrNull() as? JSLiteralExpression ?: return@forEach
                val annotationText = literal.valueAsPropertyName ?: return@forEach

                val function = callExpression.arguments.filterIsInstance<JSFunction>().firstOrNull() ?: return@forEach

                val module = ModuleUtilCore.findModuleForPsiElement(token) ?: return

                DumbService.getInstance(module.project).runReadActionInSmartMode {

                    val usages = findUsages(function)

                    CucumberJavaScriptUtil.getContentFromLiteralText(annotationText)
                    CucumberJavaScriptUtil.isRegexpString(annotationText)

                    CucumberJavaScriptUtil.getCucumberStepTextFromElement(callExpression)

                    buildMarkers(token, usages, annotationText, result)
                }
            }
    }
}
