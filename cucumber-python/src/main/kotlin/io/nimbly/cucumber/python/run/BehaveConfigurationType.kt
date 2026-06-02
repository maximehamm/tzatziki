/*
 * Cucumber for Python
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

/** Run-configuration type for executing behave feature files. */
class BehaveConfigurationType : ConfigurationTypeBase(
    "CucumberPythonBehaveConfigurationType",
    "Behave",
    "Run behave (Python) feature files",
    IconLoader.getIcon("/icons/behaveRunConfig.svg", BehaveConfigurationType::class.java),
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = "Behave"
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                BehaveRunConfiguration(project, this).apply { name = "Behave" }
        })
    }

    val factory: ConfigurationFactory get() = configurationFactories.first()

    companion object {
        val INSTANCE: BehaveConfigurationType
            get() = com.intellij.execution.configurations.ConfigurationTypeUtil
                .findConfigurationType(BehaveConfigurationType::class.java)
    }
}
