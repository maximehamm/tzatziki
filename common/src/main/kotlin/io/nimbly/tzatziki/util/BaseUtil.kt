package io.nimbly.tzatziki.util

fun isEmpty(str: String?): Boolean {
    return str == null || str.length == 0
}

fun countMatches(str: String, sub: String): Int {
    if (!isEmpty(str) && !isEmpty(sub)) {
        var count = 0
        var idx = 0
        while ((str.indexOf(sub, idx).also { idx = it }) != -1) {
            ++count
            idx += sub.length
        }
        return count
    } else {
        return 0
    }
}