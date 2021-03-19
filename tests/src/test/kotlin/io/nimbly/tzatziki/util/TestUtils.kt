package io.nimbly.tzatziki.format

fun getIndexOf(contents: String, lookFor: String): Int {
    val i = contents.indexOf(lookFor)
    return if (i < 0) i else i + lookFor.length
}