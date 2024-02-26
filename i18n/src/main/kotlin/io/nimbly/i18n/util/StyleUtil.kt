package io.nimbly.i18n.util

import java.text.Normalizer
import java.util.*

enum class EStyle {
    NORMAL,
    CAMEL_CASE,
    SNAKE_CASE_LOWER,
    SNAKE_CASE_UPPER
}

fun String.detectStyle(): EStyle {
    if (this.contains(" ") || this.contains("\n"))
        return EStyle.NORMAL

    if (this.contains("_")) {
        if ("^[a-z]+(_[a-z]+)*$".toRegex().matches(this))
            return EStyle.SNAKE_CASE_LOWER

        if ("^[A-Z]+(_[A-Z]+)*$".toRegex().matches(this))
            return EStyle.SNAKE_CASE_UPPER
    }

    if (this.fromCamelCase() != this)
        return EStyle.CAMEL_CASE

    return EStyle.NORMAL
}

fun String.unescapeStyle(style: EStyle): String {
    return try {
        when (style) {
            EStyle.CAMEL_CASE ->
                this.fromCamelCase()
            EStyle.SNAKE_CASE_LOWER, EStyle.SNAKE_CASE_UPPER ->
                this.replace("_", " ")
            else ->
                this
        }
    } catch (e: Exception) {
        return this
    }
}

fun String.escapeStyle(style: EStyle, locale: Locale): String {
    return try {
        when (style) {
            EStyle.CAMEL_CASE ->
                this.toCamelCase(locale)
                    .removeAccents()
                    .replace("[^a-zA-Z_]".toRegex(), "")
            EStyle.SNAKE_CASE_LOWER, EStyle.SNAKE_CASE_UPPER ->
                this.replace(" ", "_")
                    .removeAccents()
                    .replace("[^a-zA-Z_]".toRegex(), "")
            else ->
                this
        }
    } catch (e: Exception) {
        return this
    }
}

fun String.fromCamelCase(): String {
    val regex = Regex("(?<=[a-zÀ-ö])(?=[A-ZÀ-Ö])|(?<=[A-Z])(?=[A-Z][a-zÀ-ö])")
    return this.split(regex).joinToString(" ")
}

fun String.toCamelCase(locale: Locale): String {
    val words = this
        .replace("'", " ")
        .replace("-", " ")
        .split(" ")
    val camelCased = StringBuilder(words[0].lowercase())

    for (i in 1 until words.size) {
        val word =
            if (words[i].isUpperCase())
                words[i]
            else
                words[i].lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        camelCased.append(word)
    }

    return camelCased.toString()
}

private fun String.isUpperCase(): Boolean {
    return this.isNotBlank() && this.find { !it.isUpperCase() } == null
}

fun String.removeAccents(): String {
    val normalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
    val pattern = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    return pattern.replace(normalizedString, "")
}