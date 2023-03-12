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

package io.nimbly.tzatziki.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import icons.ActionIcons
import io.nimbly.tzatziki.services.TzTagService
import io.nimbly.tzatziki.util.description
import io.nimbly.tzatziki.util.safeText
import org.jetbrains.plugins.cucumber.psi.GherkinTag

class TzTagCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val origin = parameters.position.parent
        if (origin !is GherkinTag)
            return


        // Find all tags
        val tagService = origin.project.getService(TzTagService::class.java)
        val allTags = tagService.getTags()

        //
        // Add completions
        val description = origin.name.safeText.trim().substringAfter("@")
        val filename = origin.containingFile.name
        allTags
            .filter {it.key != description}
            .toSortedMap()
                .forEach { (tagDescription, tag) ->

                val other = tag.gtags.find { it.containingFile != origin }
                val lookup = LookupElementBuilder.create(tagDescription)
                    .withPsiElement(other?.navigationElement)
                    .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {

                            presentation.icon = ActionIcons.TAG
                            presentation.itemText = tagDescription

                            var typeText = tag.gFiles.firstOrNull{ it.name == filename }?.name ?: tag.gFiles.first().name
                            if (tag.gtags.size > 1)
                                typeText += """ (+${tag.gtags.size-1})"""
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