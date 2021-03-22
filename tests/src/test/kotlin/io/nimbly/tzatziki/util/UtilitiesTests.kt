package io.nimbly.tzatziki.util

import com.intellij.openapi.util.TextRange
import junit.framework.TestCase
import kotlin.test.assertEquals

class UtilitiesTests  : TestCase() {

    fun testCollectionMove() {

        val main = mutableListOf("A", "B", "C", "D", "E", "F")

        main.clone().move(0, +1).check("B", "A", "C", "D", "E", "F")
        main.clone().move(0, +2).check("B", "C", "A", "D", "E", "F")
        main.clone().move(0, +5).check("B", "C", "D", "E", "F", "A")
        main.clone().move(0, +6).check("B", "C", "D", "E", "F", "A")

        main.clone().move(3, +1).check("A", "B", "C", "E", "D", "F")
        main.clone().move(5, +1).check("A", "B", "C", "D", "E", "F")

        main.clone().move(0, -1).check("A", "B", "C", "D", "E", "F")
        main.clone().move(5, -1).check("A", "B", "C", "D", "F", "E")
        main.clone().move(3, -2).check("A", "D", "B", "C", "E", "F")
        main.clone().move(5, -5).check("F", "A", "B", "C", "D", "E")
        main.clone().move(5, -6).check("F", "A", "B", "C", "D", "E")
    }

    fun testStringShopUp() {

        // 3 every 3
        var slices = listOf(TextRange(0, 3), TextRange(6, 9), TextRange(12, 15))
        assertEquals(
            "ABCdefGHIjklMNOpqrstu",
            "abcdefghijklmnopqrstu".shopUp(slices).toUpper())

        assertEquals(
            "ABCdefGHIjklMNO",
            "abcdefghijklmno".shopUp(slices).toUpper())

        assertEquals(
            "ABCdefG",
            "abcdefg".shopUp(slices).toUpper())

        // 3 every 3, start at 3
        slices = listOf(TextRange(3, 6), TextRange(9, 12), TextRange(15, 18))
        assertEquals(
            "abcDEFghiJKLmnoPQRstu",
            "abcdefghijklmnopqrstu".shopUp(slices).toUpper())

        assertEquals(
            "abcDEFghiJKLmnoPQRs",
            "abcdefghijklmnopqrs".shopUp(slices).toUpper())

        assertEquals(
            "abcDEFghiJKLmnoPQR",
            "abcdefghijklmnopqr".shopUp(slices).toUpper())

        assertEquals(
            "abcDEFgh",
            "abcdefgh".shopUp(slices).toUpper())

        // No slice
        slices = listOf()
        assertEquals(
            "abcdefghijklmnopqrstu",
            "abcdefghijklmnopqrstu".shopUp(slices).toUpper())

        // Single full slice
        slices = listOf(TextRange(0, 21))
        assertEquals(
            "ABCDEFGHIJKLMNOPQRSTU",
            "abcdefghijklmnopqrstu".shopUp(slices).toUpper())

        // Single full slice, but edge
        slices = listOf(TextRange(1, 20))
        assertEquals(
            "aBCDEFGHIJKLMNOPQRSTu",
            "abcdefghijklmnopqrstu".shopUp(slices).toUpper())

    }


}

private fun List<Slice>.toUpper(): String {
    val sb = StringBuilder()
    forEach {
        if (it.isInterRange)
            sb.append(it.text)
        else
            sb.append(it.text.toUpperCase())
    }
    return sb.toString()
}

private fun List<Slice>.addBracket(): String {
    val sb = StringBuilder()
    forEach {
        if (!it.isInterRange) sb.append('[')
        sb.append(it.text)
        if (!it.isInterRange) sb.append(']')
    }
    return sb.toString()
}

private fun <E> MutableList<E>.check(vararg values: E)
    = assertEquals(values.asList(), this)

private fun <E> MutableList<E>.clone(): MutableList<E>
    = mutableListOf<E>().let { it.addAll(this); it }
