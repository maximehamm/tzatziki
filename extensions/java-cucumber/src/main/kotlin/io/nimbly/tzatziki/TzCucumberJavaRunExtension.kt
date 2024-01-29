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

package io.nimbly.tzatziki

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.components.ServiceManager
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.services.TzTagService
import io.nimbly.tzatziki.services.tagService
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaRunConfiguration

class TzCucumberJavaRunExtension : RunConfigurationExtension() {

    @Throws(ExecutionException::class)
    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T & Any,
        params: JavaParameters,
        runnerSettings: RunnerSettings?) {

        if (configuration !is CucumberJavaRunConfiguration)
            return

        val state = configuration.project.tagService()
        val sel: String? = if (!state.filterByTags) null else state.selection
        if (sel.isNullOrBlank()) {
            params.vmParametersList.properties.remove("cucumber.filter.tags")
        }
        else {
            params.vmParametersList.addProperty("cucumber.filter.tags", sel)
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        return configuration is CucumberJavaRunConfiguration
    }

}