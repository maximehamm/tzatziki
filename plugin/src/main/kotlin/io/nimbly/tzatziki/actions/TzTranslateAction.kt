/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.actions

import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.net.HttpConfigurable
import io.nimbly.tzatziki.util.getDocument
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.*


class TzTranslateAction : AnAction() , DumbAware {

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor!=null
        super.update(event)
    }

    override fun actionPerformed(event: AnActionEvent) {

        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor =  CommonDataKeys.EDITOR.getData(event.dataContext) ?: return

        val startOffset: Int
        val endOffset: Int
        val text: String?
        if (editor.selectionModel.hasSelection()) {
            startOffset = editor.selectionModel.selectionStart
            endOffset = editor.selectionModel.selectionEnd
            text = editor.selectionModel.getSelectedText(false)
        }
        else {
            val offset = CommonDataKeys.CARET.getData(event.dataContext)?.offset ?: return
            val l = file.findElementAt(offset) ?: return
            startOffset = l.textRange.startOffset
            endOffset = l.textRange.endOffset
            text = l.text
        }

        if (text == null)
            return

        val translation = googleTranslate("EN", "auto", text)
            ?: return

        executeWriteCommand(file.project, "Translating with Cucumber+", Runnable {
            file.getDocument()?.replaceString(startOffset, endOffset, translation)
        })
    }

    override fun isDumbAware()
        = true

    /**
     * Google translate
     *
     * @param key               the key
     * @param targetLanguage    the target language
     * @param sourceLanguage    the source language
     * @param sourceTranslation the source translation
     * @return the string
     * @throws IOException the io exception
     */
    @Throws(IOException::class)
    fun googleTranslate(
        targetLanguage: String,
        sourceLanguage: String,
        sourceTranslation: String
    ): String? {

        val translation = callUrlAndParseResult(sourceLanguage, targetLanguage, sourceTranslation)

        // Update translation
        if (translation.isNullOrEmpty())
            return null

        return translation
    }

    private fun callUrlAndParseResult(langFrom: String, langTo: String, sentence: String): String? {

        val newlineChar =
            if (sentence.contains("\r\n")) "\r\n"
            else if (sentence.contains("\r")) "\r"
            else "\n"

        val initialSpaces = sentence.split(newlineChar).map { it.length - it.trimStart().length }
        val containsQuotes = sentence.contains('"')

        val tempsep = "(XXXXXXX)"
        val sentence2 = sentence.replace(newlineChar, tempsep)

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

        val sentence3 = parseResult(response.toString())
            ?.replace(tempsep, newlineChar)
            ?: return null

        var sentence4 =  sentence3.split(newlineChar)
            .mapIndexed { i: Int, line: String -> " ".repeat(initialSpaces.getOrElse(i) { 0 }) + line.trimStart() }
            .joinToString(newlineChar)

        if (containsQuotes) {
            sentence4 = sentence4.replace("”", "\"").replace("“", "\"")
        }

        return sentence4
    }

    private fun parseResult(inputJson: String): String? {
        val elt = JsonParser.parseString(inputJson)
        if (elt == null || !elt.isJsonArray) return null

        val jsonArray = elt.asJsonArray
        if (jsonArray.size() < 1) return null

        val elt2 = jsonArray[0]
        if (!elt2.isJsonArray) return null

        val jsonArray2 = elt2.asJsonArray
        if (jsonArray2.size() < 1) return null

        val elt3 = jsonArray2[0]
        if (!elt3.isJsonArray) return null

        val jsonArray3 = elt3.asJsonArray
        if (jsonArray3.size() < 1) return null

        val elt4 = jsonArray3[0]
        if (!elt4.isJsonPrimitive) return null

        return elt4.asString
    }

    fun executeWriteCommand(project: Project, text: String, runnable: Runnable) {
        CommandProcessor.getInstance().executeCommand(
            project, {
                val application =
                    ApplicationManager.getApplication()
                application.runWriteAction {
                    runnable.run()
                }
            },
            text, "Cucumber+"
        )
    }
}