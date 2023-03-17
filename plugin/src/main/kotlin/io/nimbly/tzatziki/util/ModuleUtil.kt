package io.nimbly.tzatziki.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

fun Project.getModuleManager(): ModuleManager
    = getService(ModuleManager::class.java)

