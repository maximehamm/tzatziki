package io.nimbly.i18n.translation.engines.microsoft

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.*
import io.nimbly.i18n.util.nullIfEmpty
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class MicrosoftEngineFree : IEngine {

    override val type = EEngine.MICROSOFT
    override fun needApiKey() = true
    override fun label() = "Microsoft Translator"
    override fun documentation() = """<html>
        Free of charge. 500 000 character limit / month. 1000 requests / hour.<br/>
        Subscribe <a href='https://rapidapi.com/microsoft-azure-org-microsoft-cognitive-services/api/microsoft-translator-text/pricing'>here</a> and receive an API key !
    </html>""".trimIndent()

    override fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String
    ): Translation? {

        val apiKey = TranslationPlusSettings.getSettings().keys[type]
            ?: return null

        val json = JsonArray()
        json.add(JsonObject().apply { addProperty("Text", textToTranslate) })
        val body = Gson().toJson(json).toRequestBody("application/json".toMediaType())

        val from = if (sourceLanguage == Lang.AUTO.code) "" else "&suggestedFrom=$sourceLanguage"
        val request = Request.Builder()
            .url("https://microsoft-translator-text.p.rapidapi.com/translate?to=$targetLanguage&api-version=3.0$from&profanityAction=NoAction&textType=plain")
            .post(body)
            .addHeader("content-type", "application/json")
            .addHeader("X-RapidAPI-Key", apiKey)
            .addHeader("X-RapidAPI-Host", "microsoft-translator-text.p.rapidapi.com")
            .build()

        val client = OkHttpClient()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (response.isSuccessful && responseBody != null) {

            val translationResponse = Gson().fromJson(responseBody, Array<JsonData>::class.java)
            val translatedText = translationResponse.first().translations.first().text
            val detectedSourceLanguage = translationResponse.first().detectedLanguage.language.lowercase()

            return Translation(translatedText, detectedSourceLanguage)

        } else {
            val msg = responseBody?.extractMessage()
                ?: response.message.nullIfEmpty()
                ?: (if (response.code == 403) "Forbidden" else "Http error")
            throw TranslationException("${response.code}: $msg")
        }

    }

    fun String.extractMessage(): String? {
        val jsonObject = JsonParser.parseString(this).asJsonObject

        fun findMessage(jsonObj: JsonObject): String? {
            for ((key, value) in jsonObj.entrySet()) {
                if (value.isJsonObject) {
                    val message = findMessage(value.asJsonObject)
                    if (message != null) return message
                } else if (key == "message") {
                    return value.asString
                }
            }
            return null
        }

        return findMessage(jsonObject)
    }

    override fun languages() = mapOf(
        "bg" to "Bulgarian",
        "cs" to "Czech",
        "da" to "Danish",
        "de" to "German",
        "el" to "Greek",
        "en" to "English",
        "es" to "Spanish",
        "et" to "Estonian",
        "eu" to "Basque",
        "fa" to "Persian",
        "fi" to "Finnish",
        "fil" to "Filipino",
        "fr" to "French",
        "fr-ca" to "French (Canada)",
        "ga" to "Irish",
        "gl" to "Galician",
        "gom" to "Konkani",
        "gu" to "Gujarati",
        "ha" to "Hausa",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hr" to "Croatian",
        "hsb" to "Upper Sorbian",
        "ht" to "Haitian Creole",
        "hu" to "Hungarian",
        "hy" to "Armenian",
        "id" to "Indonesian",
        "ig" to "Igbo",
        "ikt" to "Inuinnaqtun",
        "is" to "Icelandic",
        "it" to "Italian",
        "iu" to "Inuktitut",
        "iu-latn" to "Inuktitut (Latin)",
        "ja" to "Japanese",
        "ka" to "Georgian",
        "kk" to "Kazakh",
        "km" to "Khmer",
        "kmr" to "Northern Kurdish",
        "kn" to "Kannada",
        "ko" to "Korean",
        "ks" to "Kashmiri",
        "ku" to "Central Kurdish",
        "ky" to "Kyrgyz",
        "ln" to "Lingala",
        "lo" to "Lao",
        "lt" to "Lithuanian",
        "lug" to "Ganda",
        "lv" to "Latvian",
        "lzh" to "Chinese (Literary)",
        "mai" to "Maithili",
        "mg" to "Malagasy",
        "mi" to "Maori",
        "mk" to "Macedonian",
        "ml" to "Malayalam",
        "mn-cyrl" to "Mongolian (Cyrillic)",
        "mn-mong" to "Mongolian (Traditional)",
        "mni" to "Manipuri",
        "mr" to "Marathi",
        "ms" to "Malay",
        "mt" to "Maltese",
        "mww" to "Hmong",
        "my" to "Burmese",
        "nb" to "Norwegian Bokmål",
        "ne" to "Nepali",
        "nl" to "Dutch",
        "nso" to "Northern Sotho",
        "nya" to "Chichewa",
        "or" to "Odia",
        "otq" to "Querétaro Otomi",
        "pa" to "Punjabi",
        "pl" to "Polish",
        "prs" to "Dari",
        "ps" to "Pashto",
        "pt" to "Portuguese (Brazil)",
        "pt-pt" to "Portuguese (Portugal)",
        "ro" to "Romanian",
        "ru" to "Russian",
        "run" to "Rundi",
        "rw" to "Kinyarwanda",
        "sd" to "Sindhi",
        "si" to "Sinhala",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "sm" to "Samoan",
        "sn" to "Shona",
        "so" to "Somali",
        "sq" to "Albanian",
        "sr-cyrl" to "Serbian (Cyrillic)",
        "sr-latn" to "Serbian (Latin)",
        "st" to "Southern Sotho",
        "sv" to "Swedish",
        "sw" to "Swahili",
        "ta" to "Tamil",
        "te" to "Telugu",
        "th" to "Thai",
        "ti" to "Tigrinya",
        "tk" to "Turkmen",
        "tlh-latn" to "Klingon (Latin)",
        "tlh-piqd" to "Klingon (pIqaD)",
        "tn" to "Tswana",
        "to" to "Tongan",
        "tr" to "Turkish",
        "tt" to "Tatar",
        "ty" to "Tahitian",
        "ug" to "Uighur",
        "uk" to "Ukrainian",
        "ur" to "Urdu",
        "uz" to "Uzbek",
        "vi" to "Vietnamese",
        "xh" to "Xhosa",
        "yo" to "Yoruba",
        "yua" to "Yucatec Maya",
        "yue" to "Cantonese (Traditional)",
        "zh-hans" to "Chinese (Simplified)",
        "zh-hant" to "Chinese (Traditional)",
        "zu" to "Zulu"
    )

    private data class DetectedLanguage(
        val language: String,
        val score: Double? = null
    )

    private data class SourceText(
        val text: String
    )

    private data class Translations(
        val text: String,
        val to: String
    )

    private data class JsonData(
        val detectedLanguage: DetectedLanguage,
        val sourceText: SourceText,
        val translations: List<Translations>
    )
}