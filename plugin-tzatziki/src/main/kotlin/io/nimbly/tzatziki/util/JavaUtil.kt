/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki.util

import com.intellij.debugger.engine.JavaDebugProcess
import java.lang.reflect.Field
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object JavaUtil {

    fun Any.invokeMethod(method: String,
                         argClasses: List<Class<*>>,
                         args: MutableList<Any?>): Any {
        val f1 = javaClass.getDeclaredMethod(method, *argClasses.toTypedArray())
        f1.isAccessible = true
        return f1.invoke(this, *args.toTypedArray())
    }

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
            @Suppress("DEPRECATION")
            f1.set(`object`, Integer.valueOf(inte))
        } catch (ignored: Exception) {
        }
    }

    fun updateAnyField(`object`: Any, field: String, icon: Any) {
        try {
            val f1 = `object`.javaClass.findField(field)
            f1.isAccessible = true
            f1.set(`object`, icon)
        } catch (ignored: Exception) {
        }
    }

    private fun Class<*>.findField(field: String?): Field {
        try {
            return this.getDeclaredField(field)
        } catch (e: Exception) {
            return superclass.findField(field)
        }
    }
}

fun isJavaPresent(): Boolean {
    return try {
        val clz: Class<JavaDebugProcess> = JavaDebugProcess::class.java
        clz.toString()
        true
    } catch (e: NoClassDefFoundError) {
        false
    }
}
fun <E> MutableList<E>.push(element: E) = add(element)

fun <E> MutableList<E>.peek() = this.lastOrNull()

fun <T> MutableList<T>.pop() = removeAt(size-1)

fun now(): LocalDate {
    return LocalDate.now()
}

fun <T, C : Collection<T>> C.nullIfEmpty(): C?
        = this.ifEmpty { null }

fun String?.nullIfEmpty(): String? =
    if (isNullOrEmpty()) null else this

val NOW: String
    get() = now().format(DATE_FORMAT)

val DATE_FORMAT: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("dd-MM-yyyy")

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterValuesNotNull()
    = filterValues { it != null } as Map<K, V>

class TzatzikiException(message: String): Exception(message)
