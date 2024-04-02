package io.nimbly.i18n.translation.engines

import io.nimbly.i18n.util.languagesMap

data class Lang(val code: String, val name: String) {

    override fun toString(): String {
        return name
    }

    fun isAuto() = code == "auto"

    companion object {
        val DEFAULT = Lang("en", languagesMap["en"]!!)
        val AUTO = Lang("auto", "Auto")
    }
}