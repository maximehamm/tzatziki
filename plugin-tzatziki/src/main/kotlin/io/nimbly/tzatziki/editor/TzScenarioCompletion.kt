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

package io.nimbly.tzatziki.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import icons.ActionIcons
import icons.CucumberIcons
import io.nimbly.tzatziki.services.TzTagService
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition


class TzScenarioCompletion: CompletionContributor() {

    companion object {
        private val CacheKey: Key<CachedValue<List<Step>>> = Key.create(Companion::CacheKey.javaClass.simpleName)
    }

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val step = parameters.position.parent
        if (step !is GherkinStep)
            return

        val project = step.project
        val module = ModuleUtilCore.findModuleForPsiElement(step)
            ?: return

        val tagService = project.getService(TzTagService::class.java)

        val allSteps = mutableSetOf<Step>()
        FilenameIndex
            .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, module.getGherkinScope())
            .map { vfile -> vfile.getFile(project) }
            .filterIsInstance<GherkinFile>()
            .filter { it.checkExpression(tagService.getTagsFilter()) }
            .forEach { file ->
                val steps = CachedValuesManager.getCachedValue(file, CacheKey) {

                    val steps = file.features
                        .flatMap { feature -> feature.scenarios.toList() }
                        .flatMap { scenario -> scenario.steps.toList() }
                        .map { Step(it) }
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

        //
        // Add completions
        allSteps
            .filter { it.description != description}
            .groupBy { it.description }
            .toSortedMap()
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
                                .flatMap { it.step.allTags }
                                .map { it.name }
                                .toSortedSet()
                                .joinToString(separator = " ", prefix = "  ")

                            presentation.setTailText(tags, true)
                        }
                    })

                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
        }

        //
        // Adapt and add other contributor's completions
        val allStepDescriptions = allSteps.map { it.description }
        resultSet.runRemainingContributors(parameters) { result ->
            var lookup = result.lookupElement
            val lookupString = result.lookupElement.lookupString
            if (! allStepDescriptions.contains(lookupString)) {

                if (lookup is LookupElementBuilder) {

                    lookup = lookup
                        .withIcon(CucumberIcons.Cucumber)
                        .withPresentableText(lookupString)
                        .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {

                            override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {

                                presentation.icon = CucumberIcons.Cucumber
                                presentation.itemText = lookupString
                                presentation.isItemTextBold = false

                                val obj = lookup.`object`
                                if (obj is PsiElement) {
                                    presentation.typeText = obj.containingFile.name
                                    presentation.isStrikeout = obj.isDeprecated()
                                }
                            }
                        })
                }
                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 90.0))
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

    private data class Step(val step: GherkinStep) {

        val description: String by lazy { step.description }
        val filename: String by lazy { step.containingFile.name }
        val definition: AbstractStepDefinition? by lazy { step.findDefinitions().firstOrNull() }

        val deprecated: Boolean
            get() = definition?.isDeprecated() ?: false
    }
}