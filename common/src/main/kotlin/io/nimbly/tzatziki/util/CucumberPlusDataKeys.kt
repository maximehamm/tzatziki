package io.nimbly.tzatziki.util

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.module.Module

class CucumberPlusDataKeys {

    companion object {

        // Compatibility solution : use my own DataKey !
        val MODULE = DataKey.create<Module>("module")
    }
}