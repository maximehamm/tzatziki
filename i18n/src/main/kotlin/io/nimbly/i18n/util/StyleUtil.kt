package io.nimbly.i18n.util

import java.text.Normalizer
import java.util.*

enum class EStyle {
    NORMAL,
    NORMAL_LOWER,
    NORMAL_UPPER,
    NORMAL_TITLED,
    CAMEL_CASE_LOWER,
    CAMEL_CASE_UPPER,
    SNAKE_CASE_LOWER,
    SNAKE_CASE_UPPER
}

fun String.detectStyle(psiElementSelected: Boolean): EStyle {
    if (this.contains(" ") || this.contains("\n"))
        return EStyle.NORMAL

    if (this.contains("_")) {
        if ("^[a-z]+(_[a-z]+)*$".toRegex().matches(this))
            return EStyle.SNAKE_CASE_LOWER

        if ("^[A-Z]+(_[A-Z]+)*$".toRegex().matches(this))
            return EStyle.SNAKE_CASE_UPPER
    }

    if (this.fromCamelCase() != this) {
        if (this.first().isUpperCase())
            return EStyle.CAMEL_CASE_UPPER
        else
            return EStyle.CAMEL_CASE_LOWER
    }

    // If current selected item is a PsiElement, let's prefer caml
    if (psiElementSelected) {
        if (this.isUpperCase())
            return EStyle.SNAKE_CASE_UPPER
        if (this.isLowerCase())
            return EStyle.CAMEL_CASE_LOWER
        return EStyle.CAMEL_CASE_UPPER
    }

    if (this.isLowerCase())
        return EStyle.NORMAL_LOWER

    if (this.isUpperCase())
        return EStyle.NORMAL_UPPER

    if (this.isTitleCase())
        return EStyle.NORMAL_TITLED

    return EStyle.NORMAL
}

fun String.unescapeStyle(style: EStyle): String {
    return try {
        when (style) {
            EStyle.CAMEL_CASE_UPPER, EStyle.CAMEL_CASE_LOWER ->
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
            EStyle.CAMEL_CASE_UPPER ->
                this.toCamelCase(locale)
                    .removeAccents()
                    .preserveQuotes { it.replace("[^a-zA-Z_]".toRegex(), "") }
                    .toTitleCase(locale)
            EStyle.CAMEL_CASE_LOWER ->
                this.toCamelCase(locale)
                    .removeAccents()
                    .preserveQuotes { it.replace("[^a-zA-Z_]".toRegex(), "") }
            EStyle.SNAKE_CASE_LOWER ->
                this.replace(" ", "_")
                    .removeAccents()
                    .preserveQuotes { it.replace("[^a-zA-Z_]".toRegex(), "") }
                    .lowercase(locale)
            EStyle.SNAKE_CASE_UPPER ->
                this.replace(" ", "_")
                    .removeAccents()
                    .preserveQuotes { it.replace("[^a-zA-Z_]".toRegex(), "") }
                    .uppercase(locale)
            EStyle.NORMAL_TITLED ->
                this.toTitleCase(locale)
            EStyle.NORMAL_UPPER ->
                this.uppercase(locale)
            EStyle.NORMAL_LOWER ->
                this.lowercase(locale)
            
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
    val camelCased = StringBuilder(words[0].lowercase(locale))

    for (i in 1 until words.size) {
        val word =
            if (words[i].isUpperCase())
                words[i]
            else
                words[i].lowercase(locale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        camelCased.append(word)
    }

    return camelCased.toString()
}

fun String.toTitleCase(locale: Locale): String {
    if (this.length < 2)
        return this
    return this[0].uppercase(locale) + this.substring(1)
}

fun String.isUpperCase(): Boolean {
    return this.isNotBlank() && this.all { it.isUpperCase() }
}

fun String.isLowerCase(): Boolean {
    return this.isNotBlank() && this.all { it.isLowerCase() }
}

fun String.isTitleCase(): Boolean {
    return this.isNotBlank() && this.length > 2
        && this[0].isUpperCase() && this.substring(1).isLowerCase()
}


fun String.removeAccents(): String {
    val normalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
    val pattern = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    return pattern.replace(normalizedString, "")
}