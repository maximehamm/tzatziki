package io.nimbly.i18n.translation.engines.deepl

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.Translation
import io.nimbly.i18n.translation.engines.Lang
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class DeepLEngine(override val type: EEngine) : IEngine {

    override fun needApiKey() = true
    override fun label() = "DeepL API Free"
    override fun documentation() = """<html>
        Free for developpers. 500 000 character limit / month.<br/>
        Subscribe <a href='https://www.deepl.com/en/pro#developer'>here</a> and receive an API key !
    </html>""".trimIndent()

    override fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String
    ): Translation? {

        val apiKey = TranslationPlusSettings.getSettings().keys[type]

        val client = OkHttpClient()
        val json = JsonObject()
        json.add("text", JsonArray().also { it.add(textToTranslate) })
        if (!sourceLanguage.equals(Lang.AUTO.code, true)) {
            json.addProperty("source_lang", sourceLanguage.uppercase())
        }
        json.addProperty("target_lang", targetLanguage.uppercase())

        val body = Gson().toJson(json).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://${getEndpoint()}/v2/translate") // https://api.deepl.com/v2/translate
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (response.isSuccessful && responseBody != null) {

            data class TR(val text: String, val detected_source_language: String)
            data class TRL(val translations: List<TR>)

            val translationResponse = Gson().fromJson(responseBody, TRL::class.java)
            val translatedText = translationResponse.translations.first().text
            val detectedSourceLanguage = translationResponse.translations.first().detected_source_language.lowercase()

            return Translation(translatedText, detectedSourceLanguage)

        } else {
            return null
        }

    }

    open protected fun getEndpoint()
        = "api-free.deepl.com"
}