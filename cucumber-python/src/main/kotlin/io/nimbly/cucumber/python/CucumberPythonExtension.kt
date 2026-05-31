/*
 * Cucumber for Python (POC)
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import org.jetbrains.plugins.cucumber.BDDFrameworkType
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.AbstractCucumberExtension
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * Wires the base Gherkin/cucumber framework to behave step definitions written
 * in Python, so Gherkin steps resolve to (and navigate to) their
 * `@given/@when/@then`-decorated Python functions in IntelliJ IDEA Ultimate.
 *
 * Registered as `org.jetbrains.plugins.cucumber.steps.cucumberJvmExtensionPoint`
 * (the general step-def extension point — the "Jvm" in the name is historical;
 * the JS plugin uses it too).
 */
class CucumberPythonExtension : AbstractCucumberExtension() {

    override fun getStepFileType(): BDDFrameworkType = BDDFrameworkType(PythonFileType.INSTANCE)

    override fun getStepDefinitionCreator(): StepDefinitionCreator = PythonStepDefinitionCreator()

    override fun isStepLikeFile(child: PsiElement, parent: PsiElement): Boolean = child is PyFile

    override fun isWritableStepLikeFile(child: PsiElement, parent: PsiElement): Boolean =
        child is PyFile && child.virtualFile?.isWritable == true

    /** Parse a Python step-def file into one [PythonStepDefinition] per
     *  behave-decorated top-level function. */
    override fun loadStepsFor(featureFile: PsiFile?, module: Module): List<AbstractStepDefinition> {
        val pyFile = featureFile as? PyFile ?: return emptyList()
        val result = mutableListOf<AbstractStepDefinition>()
        for (function in pyFile.topLevelFunctions) {
            val decorators = function.decoratorList?.decorators ?: continue
            val isStep = decorators.any { it.name?.lowercase() in PythonStepDefinition.STEP_DECORATORS }
            if (isStep) result += PythonStepDefinition(function)
        }
        return result
    }

    /** behave convention: step defs live under a `steps/` directory next to the
     *  feature files (typically `features/steps/`). Also scans the feature's own
     *  directory as a fallback. */
    override fun getStepDefinitionContainers(featureFile: GherkinFile): Collection<PsiFile> {
        val featureDir = featureFile.containingDirectory ?: return emptyList()
        val result = LinkedHashSet<PsiFile>()

        val stepsDirs = listOfNotNull(
            featureDir.findSubdirectory("steps"),
            featureDir.parentDirectory?.findSubdirectory("steps"),
        )
        for (dir in stepsDirs) collectPyFiles(dir, result)
        // Fallback: .py files sitting directly beside the feature.
        featureDir.files.forEach { if (it is PyFile) result += it }
        return result
    }

    private fun collectPyFiles(dir: PsiDirectory, into: MutableSet<PsiFile>) {
        dir.files.forEach { if (it is PyFile) into += it }
        dir.subdirectories.forEach { collectPyFiles(it, into) }
    }
}
