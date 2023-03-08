package io.nimbly.tzatziki.generation

import java.text.Normalizer

fun String.stripAccents(): String {
    var string = Normalizer.normalize(this, Normalizer.Form.NFD)
    string = Regex("\\p{InCombiningDiacriticalMarks}+").replace(string, "")
    return  string
}

fun String.fixName(): String {
    return if (this == "*") return "When" else this
}