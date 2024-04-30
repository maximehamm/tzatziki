package io.nimbly.i18n.translation.engines.baidu

import com.google.gson.Gson
import io.nimbly.i18n.translation.engines.*
import io.nimbly.i18n.util.extractMessage
import io.nimbly.i18n.util.nullIfEmpty
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.digest.DigestUtils

open class BaiduTranslate : IEngine {

    override val type = EEngine.BAIDU
    override fun needApiKey() = true
    override fun label() = "Baidu Translate"
    override fun documentation() = """<html>
        Free of charge. XXX 000 character limit / month. XXX 000 requests / month.<br/>
        Subscribe <a href='http://developers.baidu.com/'>here</a> and receive an API key !
    </html>""".trimIndent()

    override fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String
    ): Translation? {

//        val apiKey = TranslationPlusSettings.getSettings().keys[type]
//            ?: return null
        val apiKey = "5TCEJj2txhSZdZ2TuiXt"

        val appId = "20200812000540942"
        val appKey = "5TCEJj2txhSZdZ2TuiXt"
        val salt = System.currentTimeMillis()

        val url = "https://fanyi-api.baidu.com/api/trans/vip/translate"
        val requestBody = mapOf(
            "q" to textToTranslate,
            "from" to sourceLanguage,
            "to" to targetLanguage,
            "appid" to appId,
            "salt" to salt,
            "sign" to DigestUtils.md5Hex("$appId$textToTranslate$salt$appKey")
        )

        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(Gson().toJson(requestBody).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (response.isSuccessful && responseBody != null) {

            val translationResponse = Gson().fromJson(responseBody, TranslationResponse::class.java)
            val translatedText = translationResponse.transResult[0].dst

            return Translation(translatedText, sourceLanguage)

        } else {
            val msg = responseBody?.extractMessage()
                ?: response.message.nullIfEmpty()
                ?: (if (response.code == 403) "Forbidden" else "Http error")
            throw TranslationException("${response.code}: $msg")
        }
    }

    override fun languages() = mapOf(
        "af" to "Afrikaans",
        "am" to "Amharic",
        "ar" to "Arabic",
        "az" to "Azerbaijani",
        "be" to "Belarusian",
        "bg" to "Bulgarian",
        "bn" to "Bengali",
        "bm" to "Bambara",
        "bs" to "Bosnian",
        "ca" to "Catalan",
        "ceb" to "Cebuano",
        "ckb" to "Kurdish (Sorani)",
        "co" to "Corsican",
        "cs" to "Czech",
        "cy" to "Welsh",
        "da" to "Danish",
        "de" to "German",
        "dv" to "Dhivehi",
        "el" to "Greek",
        "en" to "English",
        "eo" to "Esperanto",
        "es" to "Spanish",
        "et" to "Estonian",
        "eu" to "Basque",
        "fa" to "Persian",
        "fi" to "Finnish",
        "fil" to "Filipino (Tagalog)",
        "fr" to "French",
        "fy" to "Frisian",
        "ga" to "Irish",
        "gd" to "Scots Gaelic",
        "gl" to "Galician",
        "gn" to "Guarani",
        "gu" to "Gujarati",
        "ha" to "Hausa",
        "haw" to "Hawaiian",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hmn" to "Hmong",
        "hr" to "Croatian",
        "ht" to "Haitian Creole",
        "hu" to "Hungarian",
        "hy" to "Armenian",
        "ig" to "Igbo",
        "is" to "Icelandic",
        "it" to "Italian",
        "iw" to "Hebrew",
        "ja" to "Japanese",
        "jv" to "Javanese",
        "ka" to "Georgian",
        "kkn" to "Kannada",
        "kk" to "Kazakh",
        "km" to "Khmer",
        "kn" to "Kannada",
        "ko" to "Korean",
        "kri" to "Krio",
        "ku" to "Kurdish",
        "ky" to "Kyrgyz",
        "la" to "Latin",
        "lb" to "Luxembourgish",
        "lg" to "Luganda",
        "lo" to "Lao",
        "lt" to "Lithuanian",
        "lus" to "Mizo",
        "lv" to "Latvian",
        "mai" to "Maithili",
        "mg" to "Malagasy",
        "mni-Mtei" to "Meiteilon (Manipuri)",
        "mk" to "Macedonian",
        "ml" to "Malayalam",
        "mn" to "Mongolian",
        "mr" to "Marathi",
        "ms" to "Malay",
        "mt" to "Maltese",
        "my" to "Myanmar (Burmese)",
        "ne" to "Nepali",
        "nl" to "Dutch",
        "no" to "Norwegian",
        "nso" to "Sepedi",
        "ny" to "Nyanja (Chichewa)",
        "or" to "Odia (Oriya)",
        "om" to "Oromo",
        "pa" to "Punjabi",
        "pl" to "Polish",
        "ps" to "Pashto",
        "pt" to "Portuguese (Portugal, Brazil)",
        "qu" to "Quechua",
        "ro" to "Romanian",
        "ru" to "Russian",
        "rw" to "Kinyarwanda",
        "sa" to "Sanskrit",
        "sd" to "Sindhi",
        "si" to "Sinhala (Sinhalese)",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "sm" to "Samoan",
        "sn" to "Shona",
        "so" to "Somali",
        "sq" to "Albanian",
        "sr" to "Serbian",
        "st" to "Sesotho",
        "su" to "Sundanese",
        "sv" to "Swedish",
        "sw" to "Swahili",
        "ta" to "Tamil",
        "te" to "Telugu",
        "tg" to "Tajik",
        "th" to "Thai",
        "ti" to "Tigrinya",
        "tk" to "Turkmen",
        "tl" to "Tagalog (Filipino)",
        "tr" to "Turkish",
        "ug" to "Uyghur",
        "uk" to "Ukrainian",
        "ur" to "Urdu",
        "uz" to "Uzbek",
        "vi" to "Vietnamese",
        "xh" to "Xhosa",
        "yi" to "Yiddish",
        "yo" to "Yoruba",
        "zu" to "Zulu"
    )

    data class TranslationResponse(val transResult: List<TranslationItem>)
    data class TranslationItem(val src: String, val dst: String)
}