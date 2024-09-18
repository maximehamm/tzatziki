package io.nimbly.tzatziki.util

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext

fun emptyConfigurationContext()
        =  ConfigurationContext.getFromContext(SimpleDataContext.builder().build(), ActionPlaces.UNKNOWN)
//        = TzDataContext().configutation()
