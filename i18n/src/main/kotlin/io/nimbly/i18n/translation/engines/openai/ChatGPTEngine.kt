package io.nimbly.i18n.translation.engines.openai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.*
import io.nimbly.i18n.util.extractMessage
import io.nimbly.i18n.util.nullIfEmpty
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class ChatGPTEngine : IEngine {

    override val type = EEngine.CHATGPT
    override fun needApiKey() = true
    override fun label() = "ChatGPT"
    override fun documentation() = """<html>
        Pay-per-use service based on tokens count.<br/>
        Subscribe <a href='https://platform.openai.com/playground'>here</a> and receive an API key !
    </html>""".trimIndent()

    override fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String
    ): Translation? {

        val apiKey = TranslationPlusSettings.getSettings().keys[type]
            ?: return null

        val from = if (sourceLanguage == Lang.AUTO.code) "" else languages()[sourceLanguage] ?: ""
        val to = languages()[targetLanguage] ?: throw TranslationException("Unknown language")
        val data = mapOf(
            "prompt" to "Translate the following $from text to $to:\n```\n$textToTranslate\n```",
            "model" to "gpt-3.5-turbo-instruct",
            "max_tokens" to 300)
        val json = Gson().toJson(data)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/completions")
            .post(body)
            .addHeader("content-type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val client = OkHttpClient()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (response.isSuccessful && responseBody != null) {
            return Translation(responseBody, from)
        } else {
            throw TranslationException(responseBody?.extractMessage() ?: "No translation found")
        }
    }

    override fun languages() = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "nl" to "Dutch",
        "ru" to "Russian",
        "zh" to "Chinese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "bg" to "Bulgarian",
        "cs" to "Czech",
        "da" to "Danish",
        "el" to "Greek",
        "et" to "Estonian",
        "fi" to "Finnish",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hu" to "Hungarian",
        "id" to "Indonesian",
        "lt" to "Lithuanian",
        "lv" to "Latvian",
        "no" to "Norwegian",
        "pl" to "Polish",
        "pt" to "Portuguese",
        "ro" to "Romanian",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "sv" to "Swedish",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "vi" to "Vietnamese"
    )


}