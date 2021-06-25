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
import io.nimbly.tzatziki.psi.description
import io.nimbly.tzatziki.psi.getFile
import io.nimbly.tzatziki.psi.getGherkinScope
import io.nimbly.tzatziki.psi.safeText
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzScenarioCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        val step = parameters.position.parent
        if (step !is GherkinStep)
            return

        val project = step.project
        val module = ModuleUtilCore.findModuleForPsiElement(step)
            ?: return

        data class Item(
            val description: String,
            val filename: String
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
                        .map { Item(it.description, it.containingFile.name) }
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

        allSteps2
            .forEach { (stepDescription, items) ->
                var typeText = items.firstOrNull{ it.filename == filename }?.filename ?: items[0].filename
                if (items.size > 1)
                    typeText += """ (+${items.size-1})"""

                val lookup = LookupElementBuilder.create(stepDescription)
                    .withTypeText(typeText)
                    .withIcon(ActionIcons.CUCUMBER_PLUS_16)
                resultSet.addElement(lookup)
        }

        val allStepDescriptions = allSteps.map { it.description }
        resultSet.runRemainingContributors(parameters) {
            var lookup = it.lookupElement
            if (! allStepDescriptions.contains(lookup.lookupString)) {
                if (lookup is LookupElementBuilder) {
                    lookup = lookup.withIcon(CucumberIcons.Cucumber)
                    val obj = lookup.`object`
                    if (obj is PsiElement) {
                        lookup = lookup.withTypeText(obj.containingFile.name)
                    }
                }
                resultSet.addElement(lookup)
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