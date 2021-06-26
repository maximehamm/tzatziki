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
import com.intellij.codeInsight.lookup.LookupElementBuilder
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

        data class Item(
            val step: GherkinStep,
            val description: String,
            val filename: String,
            val definition: AbstractStepDefinition?,
            val tags: Set<String>,
            val deprecated: Boolean
        )

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
                        .map {
                            val definition = it.findDefinitions().firstOrNull()
                            val tags = it.allSteps.map { it.name }.toSet()
                            Item(
                                step = it,
                                description = it.description,
                                filename = it.containingFile.name,
                                tags = tags,
                                definition = definition,
                                deprecated = definition?.isDeprecated() ?: false
                        ) }
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

                var typeText = items.firstOrNull{ it.filename == filename }?.filename ?: items[0].filename
                if (items.size > 1)
                    typeText += """ (+${items.size-1})"""

                val deprecated = items.firstOrNull { it.deprecated }?.deprecated ?: false
                val priority = if (deprecated) 50.0 else 100.0
                val otherStep = items.find { it.step != step }?.step

                val tags = items
                    .flatMap { it.step.allSteps }
                    .map { it.name }
                    .toSortedSet()
                    .joinToString(separator = " ", prefix = "  ")

                val lookup = LookupElementBuilder.create(stepDescription)
                    .withTypeText(typeText)
                    .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                    .withStrikeoutness(deprecated)
                    .withTailText(tags, true)
                    .withPsiElement(otherStep?.stepHolder)

                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, priority))
        }

        //
        // Add and adapt other contributor's completions
        val allStepDescriptions = allSteps.map { it.description }
        resultSet.runRemainingContributors(parameters) {
            var lookup = it.lookupElement
            if (! allStepDescriptions.contains(lookup.lookupString)) {

                var priority = 100.0
                if (lookup is LookupElementBuilder) {

                    lookup = lookup.withIcon(CucumberIcons.Cucumber)
                    val obj = lookup.`object`
                    if (obj is PsiElement) {

                        val deprecated = allSteps
                            .firstOrNull() { it.definition?.element == lookup.psiElement }
                            ?.deprecated ?: false
                        if (deprecated) priority = 50.0

                        lookup = lookup
                            .withTypeText(obj.containingFile.name)
                            .withStrikeoutness(deprecated)
                    }
                }
                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, priority))
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
}