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

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.psi.row
import io.nimbly.tzatziki.psi.rowNumber
import io.nimbly.tzatziki.psi.table
import io.nimbly.tzatziki.util.ellipsis
import io.nimbly.tzatziki.util.findPreviousSiblingsOfType
import io.nimbly.tzatziki.util.parentOfTypeIs
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaRunConfiguration
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaScenarioRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.*

class TzCucumberJavaRunConfigurationProducer : CucumberJavaScenarioRunConfigurationProducer() {

    override fun setupConfigurationFromContext(
        configuration: CucumberJavaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {

        val element = sourceElement.get()
        if (!element.isValid) return false
        val parent = element.parent ?: return false
        val file = element.containingFile ?: return false

        val row = findRow(parent) ?: return false
        row.parentOfTypeIs<GherkinStepsHolder>() ?: return false

        val line = findLineNumber(file, element)

        super.setupConfigurationFromContext(configuration, context, sourceElement)

        val filePath = configuration.filePath ?: return false
        if (filePath.matches(".*:[0-9]+$".toRegex()))
            return true

        configuration.filePath = filePath + ":" + line;

        return true
    }

    override fun isConfigurationFromContext(
        configuration: CucumberJavaRunConfiguration,
        context: ConfigurationContext): Boolean {

        val element = context.psiLocation ?: return false
        val file = element.containingFile ?: return false
        val filePath = configuration.filePath ?: return false

        val configLine = filePath.substringAfterLast(":").toIntOrNull()
        val line = findLineNumber(file, element)
        if (line != configLine)
            return false

        return filePath.endsWith(":$line")
    }

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return shouldReplace(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        // Warning : this method should return within some millis...
        // If not, the run action presentation will ignore custom config name
        // build by getConfigurationName method
        // @See BaseRunConfigurationAction:update()
        return true
    }

    override fun getConfigurationName(context: ConfigurationContext): String {
        val name = super.getConfigurationName(context)
        if (name.matches(".*(- Example n°)[0-9]+\$".toRegex()))
            return name

        val element = context.psiLocation
            ?: return name
        val row = findRow(element.parent)
            ?: return name

        row.parentOfTypeIs<GherkinStepsHolder>()
            ?: return name

        var block = ""
        if (row.table.parent is GherkinExamplesBlock) {
            val title = row.table.parent.node.findChildByType(GherkinTokenTypes.TEXT)
            block = if (title != null) {
                " - ${title.text.substringBefore("\n").ellipsis(12)}"
            } else {
                " - ${row.table.parent
                    .findPreviousSiblingsOfType<GherkinTag>()
                    .joinToString(", ") { it.name }
                    .ellipsis(12)
                }"
            }
        }
        val example = row.rowNumber
        return "$name$block - Example n°$example"
    }

    private fun findLineNumber(file: PsiFile, element: PsiElement): Int? {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
            ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    private fun findRow(element: PsiElement): GherkinTableRow? {
        return element as? GherkinTableRow
            ?: element.parent as? GherkinTableRow
            ?: (element.parent as? GherkinTableCell)?.row
    }
}