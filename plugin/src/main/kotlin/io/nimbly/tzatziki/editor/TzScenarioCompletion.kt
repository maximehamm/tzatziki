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
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import icons.ActionIcons
import icons.CucumberIcons
import io.nimbly.tzatziki.psi.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

class TzScenarioCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val step = parameters.position.parent
        if (step !is GherkinStep)
            return

        val project = step.project
        val module = ModuleUtilCore.findModuleForPsiElement(step)
            ?: return

        val allSteps = mutableSetOf<Item>()
        FilenameIndex
            .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, module.getGherkinScope())
            .map { vfile -> vfile.getFile(project) }
            .filterIsInstance<GherkinFile>()
            .forEach { file ->
                val steps = CachedValuesManager.getCachedValue(file) {

                    val steps = file.features
                        .flatMap { feature -> feature.scenarios.toList() }
                        .flatMap { scenario -> scenario.steps.toList() }
                        .map { Item(step = it) }
                        .filter { it.description.isNotEmpty() }

                    CachedValueProvider.Result.create(
                        steps,
                        PsiModificationTracker.MODIFICATION_COUNT, file
                    )
                }
                allSteps.addAll(steps)
            }

        val description = step.description.safeText.trim()
        val filename = step.containingFile.name

        val allSteps2: Map<String, List<Item>>
            = allSteps
                .filter { it.description != description}
                .groupBy { it.description }

        //
        // Add completion
        allSteps2
            .forEach { (stepDescription, items) ->

                val otherStep = items.find { it.step != step }?.step
                val lookup = LookupElementBuilder.create(stepDescription)
                    .withPsiElement(otherStep?.stepHolder)
                    .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                    .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {

                            presentation.icon = ActionIcons.CUCUMBER_PLUS_16
                            presentation.itemText = stepDescription

                            var typeText = items.firstOrNull{ it.filename == filename }?.filename ?: items[0].filename
                            if (items.size > 1)
                                typeText += """ (+${items.size-1})"""
                            presentation.typeText = typeText

                            val deprecated = items.firstOrNull { it.deprecated }?.deprecated ?: false
                            presentation.isStrikeout = deprecated

                            val tags = items
                                .flatMap { it.step.allSteps }
                                .map { it.name }
                                .toSortedSet()
                                .joinToString(separator = " ", prefix = "  ")

                            presentation.setTailText(tags, true)
                        }
                    })

                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
        }

        //
        // Add and adapt other contributor's completions
        val allStepDescriptions = allSteps.map { it.description }
        resultSet.runRemainingContributors(parameters) {
            var lookup = it.lookupElement
            if (! allStepDescriptions.contains(lookup.lookupString)) {

                if (lookup is LookupElementBuilder) {

                    lookup = lookup
                        .withIcon(CucumberIcons.Cucumber)
                        .withPresentableText(it.lookupElement.lookupString)
                        .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                            override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {

                                presentation.icon = CucumberIcons.Cucumber
                                presentation.itemText = it.lookupElement.lookupString
                                presentation.isItemTextBold = false

                                val obj = lookup.`object`
                                if (obj is PsiElement) {

                                    presentation.typeText = obj.containingFile.name

                                    val deprecated = allSteps
                                        .firstOrNull() { it.definition?.element == lookup.psiElement }
                                        ?.deprecated ?: false
                                    presentation.isStrikeout = deprecated
                                }
                            }
                        })
                }
                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
            }
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

    private data class Item(val step: GherkinStep) {
        val description: String by lazy { step.description }
        val filename: String by lazy { step.containingFile.name }
        val definition: AbstractStepDefinition? by lazy { step.findDefinitions().firstOrNull() }
        val deprecated: Boolean by lazy { definition?.isDeprecated() ?: false }
    }
}