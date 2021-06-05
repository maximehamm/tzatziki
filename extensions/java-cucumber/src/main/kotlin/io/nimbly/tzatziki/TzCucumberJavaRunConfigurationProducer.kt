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
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.psi.row
import io.nimbly.tzatziki.psi.rowNumber
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaRunConfiguration
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaScenarioRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell

class TzCucumberJavaRunConfigurationProducer : CucumberJavaScenarioRunConfigurationProducer() {

    override fun setupConfigurationFromContext(
        configuration: CucumberJavaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {

        val element = sourceElement.get()
        val cell = element.parent as? GherkinTableCell
            ?: return false

        val line = getLineNumber(element)
        val example = cell.row.rowNumber

        super.setupConfigurationFromContext(configuration, context, sourceElement)

        configuration.filePath = configuration.filePath + ":" + line;
        configuration.name = configuration.name + " - Example #" + example

        return true
    }

    override fun isConfigurationFromContext(
        configuration: CucumberJavaRunConfiguration,
        context: ConfigurationContext
    ): Boolean {

        val element = context.psiLocation ?:
            return false

        val cell = element.parent as? GherkinTableCell
            ?: return false

        val example = cell.row.rowNumber
        return configuration.name.endsWith(" - Example #$example")
    }

    private fun getLineNumber(element: PsiElement): Int? {
        val document = PsiDocumentManager.getInstance(element.containingFile.project).getDocument(element.containingFile)
            ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }
}