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

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.lang.javascript.psi.util.JSProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.psi.row
import io.nimbly.tzatziki.psi.rowNumber
import org.jetbrains.plugins.cucumber.javascript.run.CucumberJavaScriptRunConfiguration
import org.jetbrains.plugins.cucumber.javascript.run.CucumberJavaScriptRunConfigurationType
import org.jetbrains.plugins.cucumber.psi.*

//@See org.jetbrains.plugins.cucumber.javascript.run.CucumberJavaScriptRunConfigurationProducer
class TzCucumberJavascriptRunConfigurationProducer : LazyRunConfigurationProducer<CucumberJavaScriptRunConfiguration>() {

    override fun getConfigurationFactory()
        = CucumberJavaScriptRunConfigurationType.getInstance().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: CucumberJavaScriptRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>): Boolean {

        val element = context.psiLocation
            ?: return false

        val cell = element.parent as? GherkinTableCell
            ?: return false

        val line = getLineNumber(element)
        val example = cell.row.rowNumber

        val stepsHolder = PsiTreeUtil.getParentOfType<PsiElement>(
            element, GherkinScenario::class.java, GherkinScenarioOutline::class.java) as GherkinStepsHolder?

        if (stepsHolder != null) {

            val container = getFileOrDirectoryToRun(element)
            val workingDir = guessWorkingDirectory(configuration.project, container)
            if (workingDir != null)
                configuration.setWorkingDirectory(workingDir)

            val configurationPrefix = if (stepsHolder is GherkinScenarioOutline) "Scenario Outline: " else "Scenario: "
            configuration.name =  configurationPrefix +
                    StringUtil.shortenTextWithEllipsis(stepsHolder.scenarioName, 30, 0) +
                    " - Example #" + example
            configuration.filePath = getPath(container) + ":" + line;

            var nameFilter = String.format("^%s$", StringUtil.escapeToRegexp(stepsHolder.scenarioName))
            if (stepsHolder is GherkinScenarioOutline) {
                nameFilter = nameFilter.replace("\\\\<.*?\\\\>".toRegex(), ".*")
            }
            configuration.nameFilter = nameFilter
            return true
        }
        else if (element.containingFile !is GherkinFile && (element !is PsiDirectory || !hasParentCalledFeatures(element))) {
            return false
        }
        else {

            val container = getFileOrDirectoryToRun(element)
            val workingDir = guessWorkingDirectory(configuration.project, container)
            if (workingDir != null) {
                configuration.setWorkingDirectory(workingDir)
            }
            configuration.name = container.name + " - Example #" + example
            configuration.filePath = getFileToRun(element) + ":" + line;
            return true
        }

    }

    override fun isConfigurationFromContext(
        configuration: CucumberJavaScriptRunConfiguration,
        context: ConfigurationContext
    ): Boolean {

        val element = context.psiLocation ?:
        return false

        val cell = element.parent as? GherkinTableCell
            ?: return false

        val example = cell.row.rowNumber
        return configuration.name.endsWith(" - Example #$example")
    }

    override fun isPreferredConfiguration(self: ConfigurationFromContext?, other: ConfigurationFromContext?): Boolean {
        return true
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return true
    }

    private fun getFileOrDirectoryToRun(element: PsiElement)
        = PsiTreeUtil.getParentOfType(element, PsiFileSystemItem::class.java, false) as PsiFileSystemItem

    private fun getPath(fileOrDirectory: PsiFileSystemItem)
        = FileUtil.toSystemIndependentName(fileOrDirectory.virtualFile.path)

    private fun getFileToRun(element: PsiElement)
        = getPath(getFileOrDirectoryToRun(element))

    private fun guessWorkingDirectory(project: Project, psiFileItem: PsiFileSystemItem): String? {
        val virtualFile = psiFileItem.virtualFile
            ?: return null

        val packageJson = JSProjectUtil.findFileUpToContentRoot(project, virtualFile, *arrayOf("package.json"))
        if (packageJson != null) {
            val directory = packageJson.parent
            if (directory != null) {
                return directory.path
            }
        }
        return null
    }

    private fun hasParentCalledFeatures(directory: PsiDirectory): Boolean {
        var dir: PsiDirectory? = directory
        while (dir != null) {
            if (dir.name == "features") {
                return true
            }
            dir = dir.parentDirectory
        }
        return false
    }

    private fun getLineNumber(element: PsiElement): Int? {
        val document = PsiDocumentManager.getInstance(element.containingFile.project).getDocument(element.containingFile)
            ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }
}