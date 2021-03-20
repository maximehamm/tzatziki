package io.nimbly.tzatziki.util

import junit.framework.TestCase
import kotlin.test.assertEquals

class CollectionUtilsTests  : TestCase() {

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


}

private fun <E> MutableList<E>.check(vararg values: E)
    = assertEquals(values.asList(), this)

private fun <E> MutableList<E>.clone(): MutableList<E>
    = mutableListOf<E>().let { it.addAll(this); it }
