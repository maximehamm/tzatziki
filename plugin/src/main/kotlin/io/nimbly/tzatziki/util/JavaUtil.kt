package io.nimbly.tzatziki.util

import java.lang.Exception
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object JavaUtil {

    fun updateField(`object`: Any, field: String?, bool: Boolean) {
        try {
            val f1 = `object`.javaClass.getDeclaredField(field)
            f1.isAccessible = true
            f1.setBoolean(`object`, bool)
        } catch (ignored: Exception) {
        }
    }

    fun updateField(`object`: Any, field: String?, inte: Int) {
        try {
            val f1 = `object`.javaClass.getDeclaredField(field)
            f1.isAccessible = true
            f1.setInt(`object`, inte)
        } catch (ignored: Exception) {
        }
    }
}

fun <E> MutableList<E>.push(element: E) = add(element)

fun <E> MutableList<E>.peek() = this.lastOrNull()

fun <T> MutableList<T>.pop() = removeAt(size-1)

fun now(): LocalDate {
    return LocalDate.now()
}

val NOW: String
    get() = now().format(DATE_FORMAT)

val DATE_FORMAT: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("dd-MM-yyyy")