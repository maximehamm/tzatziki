/*
 * I18N +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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
package io.nimbly.i18n.translation.engines.google

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.util.net.HttpConfigurable
import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.Translation
import io.nimbly.i18n.util.nullIfEmpty
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder

class GoogleEngineFree(override val type: EEngine) : IEngine {

    override fun needApiKey() = false
    override fun label() = "Google Translate"
    override fun documentation() = """<html>
        Use of simple HTTP query. So far no restrictions.<br/>Google can block requests in case of excessive use.
    </html>""".trimIndent()

    override fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String
    ): Translation? {

        val t = callUrlAndParseResult(sourceLanguage, targetLanguage, textToTranslate)
            ?: return null

        if (t.translated.isEmpty())
            return null

        return t
    }

    private fun callUrlAndParseResult(langFrom: String, langTo: String, sentence: String): Translation? {

        val newlineChar =
            if (sentence.contains("\r\n")) "\r\n"
            else if (sentence.contains("\r")) "\r"
            else "\n"

        val initialSpaces = sentence.split(newlineChar).map { it.length - it.trimStart().length }
        val containsQuotes = sentence.contains('"')
        val endWithReturn = sentence.endsWith(newlineChar)

        val tempsep = "(XXXXXXX)"
        val sentence2 = sentence.trim().replace(newlineChar, tempsep)

        val url = "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + URLEncoder.encode(langFrom, "UTF-8") +
                "&tl=" + URLEncoder.encode(langTo, "UTF-8") +
                "&dt=t&q=" + URLEncoder.encode(sentence2, "UTF-8")

        val con = HttpConfigurable.getInstance().openConnection(url)
        con.setRequestProperty("User-Agent", "Mozilla/5.0")

        val input = BufferedReader(InputStreamReader(con.getInputStream(), "UTF-8"))
        var inputLine: String?
        val response = StringBuffer()

        while ((input.readLine().also { inputLine = it }) != null) {
            response.append(inputLine)
        }
        input.close()

        val parsed = parseResult(response.toString(), langFrom)

        val sentence3 = parsed?.translated
            ?.replace(Regex("\\(\\s?XXXXXXX\\s?\\)"), newlineChar)
            ?: return null

        var sentence4 = sentence3.split(newlineChar)
            .mapIndexed { i: Int, line: String -> " ".repeat(initialSpaces.getOrElse(i) { 0 }) + line.trimStart() }
            .joinToString(newlineChar)

        if (containsQuotes) {
            sentence4 = sentence4.replace("”", "\"").replace("“", "\"")
        }

        if (endWithReturn) {
            sentence4 += newlineChar
        }

        return Translation(sentence4, parsed.sourceLanguageIndentified)
    }

    private fun parseResult(inputJson: String, langFrom: String): Translation? {

        val elt = JsonParser.parseString(inputJson)
        if (elt == null || !elt.isJsonArray) return null

        val pretty = GsonBuilder().setPrettyPrinting().create().toJson(elt)

        val jsonArray = elt.asJsonArray
        if (jsonArray.size() < 1) return null

        val elt2 = jsonArray[0]
        if (!elt2.isJsonArray) return null

        val jsonArray2 = elt2.asJsonArray
        if (jsonArray2.size() < 1) return null

        val txt = StringBuilder()
        jsonArray2.forEach { elt3 ->

            if (!elt3.isJsonArray) return@forEach

            val jsonArray3 = elt3.asJsonArray
            if (jsonArray3.size() < 1) return@forEach

            val elt4 = jsonArray3[0]
            if (!elt4.isJsonPrimitive) return@forEach

            val asString = elt4.asJsonPrimitive.asString

            val fixUnicodeBlank = asString.replace(Regex("\\u200b"), "")
            val fixNonBreakableSpace = fixUnicodeBlank.replace(Regex("\\u00A0"), " ")
            txt.append(fixNonBreakableSpace)
        }

        val inputLanguage =
            if (jsonArray.size() > 2 && jsonArray[2].isJsonPrimitive && jsonArray[2].asJsonPrimitive.asString.length == 2)
                jsonArray[2].asJsonPrimitive.asString
            else
                langFrom

        val translation = txt.toString().trim().nullIfEmpty()
            ?: return null

        return Translation(translation, inputLanguage)
    }

}

