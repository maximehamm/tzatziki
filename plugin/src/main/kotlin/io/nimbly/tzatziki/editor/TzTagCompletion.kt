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

package io.nimbly.tzatziki.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import icons.ActionIcons
import io.nimbly.tzatziki.psi.description
import io.nimbly.tzatziki.psi.getGherkinScope
import io.nimbly.tzatziki.util.findAllTags
import io.nimbly.tzatziki.util.safeText
import org.jetbrains.plugins.cucumber.psi.GherkinTag

class TzTagCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val origin = parameters.position.parent
        if (origin !is GherkinTag)
            return

        val module = ModuleUtilCore.findModuleForPsiElement(origin)
            ?: return

        // Find all tags
        val allTags = findAllTags(module.project, module.getGherkinScope())

        //
        // Add completions
        val description = origin.description.safeText.trim()
        val filename = origin.containingFile.name
        allTags
            .filter { it.name != description}
            .groupBy { it.name }
            .toSortedMap()
            .forEach { (tagDescription, items) ->

                val other = items.find { it.tag != origin }?.tag
                val lookup = LookupElementBuilder.create(tagDescription)
                    .withPsiElement(other?.navigationElement)
                    .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                    .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {

                            presentation.icon = ActionIcons.CUCUMBER_PLUS_16
                            presentation.itemText = tagDescription

                            var typeText = items.firstOrNull{ it.filename == filename }?.filename ?: items[0].filename
                            if (items.size > 1)
                                typeText += """ (+${items.size-1})"""
                            presentation.typeText = typeText
                        }
                    })

                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
            }
    }

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet)
                        = complete(parameters, context, resultSet)
            }
        )
    }

}