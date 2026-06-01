/*
 * Cucumber for Python
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import io.nimbly.cucumber.python.CucumberPythonExtension
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.io.File

/**
 * Produces a [BehaveRunConfiguration] from the gutter / context menu on a
 * Gherkin feature — but ONLY for features backed by Python (behave) step
 * definitions. For those it takes precedence over the Cucumber.js producer,
 * which otherwise hijacks every `.feature` file.
 */
class BehaveRunConfigurationProducer : LazyRunConfigurationProducer<BehaveRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory = BehaveConfigurationType.INSTANCE.factory

    override fun setupConfigurationFromContext(
        configuration: BehaveRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val element = context.psiLocation ?: return false
        val gherkinFile = element.containingFile as? GherkinFile ?: return false
        if (!isBehaveBacked(gherkinFile)) return false

        val path = gherkinFile.virtualFile?.path ?: return false
        configuration.featurePath = path

        // Bind the feature's module + use its Python SDK, so AbstractPythonRunConfiguration
        // (and thus PyDebugRunner) resolves the interpreter itself — no internal
        // PythonSdkUtil call needed.
        val module = ModuleUtilCore.findModuleForPsiElement(gherkinFile)
        if (module != null) {
            configuration.setModule(module)
            configuration.isUseModuleSdk = true
        }
        configuration.workingDirectory = featureRootDir(path)

        val holder = PsiTreeUtil.getParentOfType(element, GherkinStepsHolder::class.java)
        if (holder != null) {
            val doc = PsiDocumentManager.getInstance(gherkinFile.project).getDocument(gherkinFile)
            val line = doc?.getLineNumber(holder.textOffset)?.plus(1) ?: -1
            configuration.lineNumber = line
            val baseName = holder.scenarioName?.trim().orEmpty()

            // Single Scenario-Outline example row? behave names expanded rows
            // "<outline> -- @<block>.<row>" (both 1-based), so target one row via -n.
            val exampleTag = exampleTagFor(element)
            if (exampleTag != null && holder is GherkinScenarioOutline) {
                configuration.scenarioName = "$baseName -- $exampleTag"
                configuration.name = "Example $exampleTag: $baseName"
            } else {
                configuration.scenarioName = baseName
                configuration.name = holder.scenarioKeyword.ifBlank { "Scenario" } + ": " + baseName.ifBlank { gherkinFile.name }
            }
        } else {
            configuration.lineNumber = -1
            configuration.scenarioName = ""
            configuration.name = gherkinFile.name
        }
        return true
    }

    override fun isConfigurationFromContext(
        configuration: BehaveRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val element = context.psiLocation ?: return false
        val gherkinFile = element.containingFile as? GherkinFile ?: return false
        if (configuration.featurePath != gherkinFile.virtualFile?.path) return false

        // Match on the behave scenario selector, not the line: a config created
        // before scenarioName existed (or the whole-feature config) must NOT be
        // reused for a scenario / single-example context.
        return configuration.scenarioName == behaveSelectorFor(element)
    }

    /** The behave `-n` selector for the run context: "" (whole feature), the
     *  scenario name, or "<outline> -- @<block>.<row>" for a single example row. */
    private fun behaveSelectorFor(element: PsiElement): String {
        val holder = PsiTreeUtil.getParentOfType(element, GherkinStepsHolder::class.java) ?: return ""
        val base = holder.scenarioName?.trim().orEmpty()
        val tag = exampleTagFor(element)
        return if (tag != null && holder is GherkinScenarioOutline) "$base -- $tag" else base
    }

    /** behave example tag "@<block>.<row>" (both 1-based) when [element] is inside a
     *  Scenario-Outline Examples data row; null otherwise. */
    private fun exampleTagFor(element: PsiElement): String? {
        val row = PsiTreeUtil.getParentOfType(element, GherkinTableRow::class.java, false) ?: return null
        val block = PsiTreeUtil.getParentOfType(row, GherkinExamplesBlock::class.java) ?: return null
        val outline = PsiTreeUtil.getParentOfType(block, GherkinScenarioOutline::class.java) ?: return null
        val blockIndex = outline.examplesBlocks.indexOf(block)
        if (blockIndex < 0) return null
        val dataRows = block.table?.dataRows ?: return null
        val rowIndex = dataRows.indexOf(row)
        if (rowIndex < 0) return null  // header row or not a data row
        return "@${blockIndex + 1}.${rowIndex + 1}"
    }

    /** Win over the Cucumber.js producer for Python-backed features. */
    override fun shouldReplace(
        self: com.intellij.execution.actions.ConfigurationFromContext,
        other: com.intellij.execution.actions.ConfigurationFromContext,
    ): Boolean = true

    override fun isPreferredConfiguration(
        self: com.intellij.execution.actions.ConfigurationFromContext,
        other: com.intellij.execution.actions.ConfigurationFromContext,
    ): Boolean = true

    /** True when the feature has at least one behave (.py) step container. */
    private fun isBehaveBacked(gherkinFile: GherkinFile): Boolean =
        CucumberPythonExtension().getStepDefinitionContainers(gherkinFile).any { it is PyFile }

    /** The directory holding the `features/` folder (behave's working dir). */
    private fun featureRootDir(featurePath: String): String {
        var dir: File? = File(featurePath).parentFile
        while (dir != null) {
            if (dir.name == "features") return dir.parentFile?.path ?: dir.path
            dir = dir.parentFile
        }
        return File(featurePath).parentFile?.path ?: "."
    }
}
