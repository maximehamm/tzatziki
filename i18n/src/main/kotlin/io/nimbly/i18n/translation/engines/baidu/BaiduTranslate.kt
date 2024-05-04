package io.nimbly.i18n.translation.engines.baidu

import com.google.gson.Gson
import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.Translation
import io.nimbly.i18n.translation.engines.TranslationException
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.security.MessageDigest
import java.util.*

// Register here : https://passport.baidu.com/v2/?reg&overseas=1
open class BaiduTranslate : IEngine {

    override val type = EEngine.BAIDU
    override fun needApiKey() = false
    override fun label() = "Baidu Translate"
    override fun documentation() = """<html>
        Free of charge. 6000 characters per request maximum.
    </html>""".trimIndent()

    override fun translate(
        targetLanguage: String,
        sourceLanguage: String,
        textToTranslate: String
    ): Translation? {

//        val appKey = TranslationPlusSettings.getSettings().keys[type]
//            ?: return null

        val appId = "20201107000610694"
        val appKey = "kwjKCeUUNitS4tnwuO1K"
        val salt = UUID.randomUUID().toString()
        val sign = md5("$appId$textToTranslate$salt$appKey").lowercase()

        val url = "https://fanyi-api.baidu.com/api/trans/vip/translate"
        val requestBody = mapOf<String, String>(
            "q" to textToTranslate,
            "from" to sourceLanguage,
            "to" to targetLanguage,
            "appid" to appId,
            "salt" to salt,
            "sign" to sign,
            "dict" to "0"
        )

        val r = HttpPost(url)
        r.entity = UrlEncodedFormEntity(map2List(requestBody), "UTF-8")

        val httpClient = HttpClients.createDefault()
        val rr = httpClient.execute(r)

        if (rr.statusLine.statusCode == 200) {

            val entity = rr.entity
            val responseBody = EntityUtils.toString(entity, "UTF-8")

            val translationResponse = Gson().fromJson(responseBody, TranslationResponse::class.java)
            if (translationResponse.error_code != null && translationResponse.error_msg != null)
                throw TranslationException(translationResponse.error_msg)

            if (translationResponse.from == null || translationResponse.trans_result == null)
                throw TranslationException("Response is empty !")

            val translatedText = translationResponse.trans_result.map { it.dst }.joinToString("\n")

            val detectecLanguage = translationResponse.from

            return Translation(translatedText, detectecLanguage)
        }
        else {
            throw TranslationException("Error occurs : " + rr.statusLine.statusCode)
        }
    }

    // See here : http://api.fanyi.baidu.com/api/trans/product/apidoc#languageList
    override fun languages() = mapOf(
        "zh" to "Chinese",
        "en" to "English",
        "yue" to "Cantonese",
        "wyw" to "Classical Chinese",
        "jp" to "Japanese",
        "kor" to "Korean",
        "fra" to "French",
        "spa" to "Spanish",
        "th" to "Thai",
        "ara" to "Arabic",
        "ru" to "Russian",
        "pt" to "Portuguese",
        "de" to "German",
        "it" to "Italian",
        "el" to "Greek language",
        "nl" to "Dutch",
        "pl" to "Polish",
        "bul" to "Bulgarian",
        "est" to "Estonian",
        "dan" to "Danish",
        "fin" to "Finnish",
        "cs" to "Czech",
        "rom" to "Romanian",
        "slo" to "Slovenia",
        "swe" to "Swedish",
        "hu" to "Hungarian",
        "cht" to "Traditional Chinese",
        "vie" to "Vietnamese"
    )

    override fun languagesToIso639() = mapOf(
        "zh" to "cn",
        "en" to "gb",
        "yue" to "hk",
        "wyw" to "cn",
        "jp" to "jp",
        "kor" to "kr",
        "fra" to "fr",
        "spa" to "es",
        "th" to "th",
        "ara" to "sa",
        "ru" to "ru",
        "pt" to "pt",
        "de" to "de",
        "it" to "it",
        "el" to "gr",
        "nl" to "nl",
        "pl" to "pl",
        "bul" to "bg",
        "est" to "ee",
        "dan" to "dk",
        "fin" to "fi",
        "cs" to "cz",
        "rom" to "ro",
        "slo" to "si",
        "swe" to "se",
        "hu" to "hu",
        "cht" to "tw",
        "vie" to "vn"
    )

    data class TranslationResponse(
        val error_code: String? = null,
        val error_msg: String? = null,
        val from: String? = null,
        val to: String? = null,
        val trans_result: List<RTranslation>? = null
    )

    data class RTranslation(
        val src: String,
        val dst: String
    )
}

fun md5(input: String): String {
    val msgDigest = MessageDigest.getInstance("MD5")
    val inputBytes = input.toByteArray(Charsets.UTF_8)
    msgDigest.update(inputBytes)
    val md5Bytes = msgDigest.digest()
    return bytes2Hex(md5Bytes)
}

private fun bytes2Hex(bytes: ByteArray): String {
    val hexDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    val chars = CharArray(bytes.size * 2)
    var idx = 0
    for (b in bytes) {
        chars[idx++] = hexDigits[(b.toInt() shr 4) and 0xf]
        chars[idx++] = hexDigits[b.toInt() and 0xf]
    }
    return String(chars)
}

fun map2List(params: Map<String, String>): List<NameValuePair> {
    val pairs = mutableListOf<NameValuePair>()
    for ((k, v) in params) {
        pairs.add(BasicNameValuePair(k, v))
    }
    return pairs
}
