package io.nimbly.tzatziki.util

fun <E> MutableList<E>.move(index: Int, step: Int): MutableList<E> {
    val item = removeAt(index)
    var i = index + step
    if (i >= size) i=size
    if (i <0) i=0
    add(i, item)
    return this
}