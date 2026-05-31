/*
 * Cucumber for Python (POC)
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Minimal step-definition creator backing the "Create step definition"
 * quick-fix. POC scope: creates a behave step-def `.py` file with the standard
 * import. The richer behaviour (inserting a templated function for the exact
 * step) is left out of this proof of concept.
 */
class PythonStepDefinitionCreator : AbstractStepDefinitionCreator() {

    override fun createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile {
        val fileName = if (name.endsWith(".py")) name else "$name.py"
        val existing = directory.findFile(fileName)
        if (existing != null) return existing
        val file = directory.createFile(fileName)
        return file
    }

    override fun getDefaultStepFileName(step: GherkinStep): String = "steps"

    override fun validateNewStepDefinitionFileName(
        project: com.intellij.openapi.project.Project,
        name: String,
    ): Boolean = name.isNotBlank()
}
