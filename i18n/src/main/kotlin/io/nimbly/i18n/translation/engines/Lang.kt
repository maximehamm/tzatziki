package io.nimbly.i18n.translation.engines

data class Lang(val code: String, val name: String) {

    override fun toString(): String {
        return name
    }

    fun isAuto() = code == "auto"

    companion object {
        val DEFAULT = Lang("en", "English")
        val AUTO = Lang("auto", "Auto")
    }
}