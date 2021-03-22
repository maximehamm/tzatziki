package io.nimbly.tzatziki.util

import com.intellij.openapi.util.TextRange

fun String.shopUp(inputSlices: List<TextRange>): List<Slice> {


    // Shrink slices
    val myRange = TextRange(0, length)
    val slices = inputSlices.mapNotNull {
        val intersection = it.intersection(myRange)
        when {
            intersection == null -> null
            intersection.length == it.length -> it
            else -> TextRange(it.startOffset, it.startOffset + intersection.length)
        }
    }
    if (slices.isEmpty())
        return listOf(Slice(this, true))

    // Process eeach slice and interleave
    val full = mutableListOf<Slice>()
    val iter = slices.iterator()
    var nextSlice: TextRange? = null
    var i=0
    while (i<length) {

        nextSlice = if (iter.hasNext()) iter.next() else null

        if (nextSlice != null) {

            if (i < nextSlice.startOffset) {

                // Add inter range
                full.add(Slice(TextRange(i, nextSlice.startOffset).substring(this), true))
            }

            // Add range
            full.add(Slice(nextSlice.substring(this), false))

            i = nextSlice.endOffset
        }
        else {
            full.add(Slice(TextRange(i, length).substring(this), true))
            i = length
        }
    }

    return full
}

class Slice(val text: String, val isInterRange: Boolean)