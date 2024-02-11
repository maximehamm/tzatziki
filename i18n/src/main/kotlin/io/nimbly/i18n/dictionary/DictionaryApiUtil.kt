package io.nimbly.i18n.dictionary

import com.google.gson.Gson
import com.intellij.util.net.HttpConfigurable
import io.nimbly.i18n.util.nullIfEmpty
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.URLEncoder

fun searchDefinition(
    source: String
): DefinitionResult {

    try {
        return callUrlAndParseResult(source)
    } catch (e: FileNotFoundException) {
        return DefinitionResult(EStatut.NOT_FOUND)
    }
}

private fun <E> List<E>.skipFirst(): List<E> {
    if (this.size < 2)
        return emptyList()

    return this.subList(1, this.size - 1)
}

private fun callUrlAndParseResult(word: String): DefinitionResult {

    val url = "https://api.dictionaryapi.dev/api/v2/entries/en/" +
            URLEncoder.encode(word, "UTF-8")

    val con = HttpConfigurable.getInstance().openConnection(url)
    con.setRequestProperty("User-Agent", "Mozilla/5.0")

    val input = BufferedReader(InputStreamReader(con.getInputStream(), "UTF-8"))
    var inputLine: String?
    val response = StringBuffer()

    while ((input.readLine().also { inputLine = it }) != null) {
        response.append(inputLine)
    }
    input.close()

    val json = response.toString()

    val gson = Gson()
    val results = gson.fromJson(json, Array<Word>::class.java).toList()

    val word = results.getOrNull(0)
        ?: return DefinitionResult(EStatut.NOT_FOUND)

    results.skipFirst().forEach { w ->
        word.meanings.addAll(w.meanings)
    }

    val shortDefinition = word.meanings .firstOrNull()?.definitions?.firstOrNull()?.definition
        ?: return DefinitionResult(EStatut.NOT_FOUND)

    if (word.meanings.isEmpty())
        return DefinitionResult(EStatut.NOT_FOUND)

    return DefinitionResult(
        status = EStatut.FOUND,
        result = word,
        shortDefinition = shortDefinition)
}

fun generateHtml(word: Word): String {
    val sb = StringBuilder()

    sb.append("<html>")
    sb.append("<head>")
    sb.append("<title>${word.word}</title>")
    sb.append("</head>")
    sb.append("<body>")
    sb.append("<h1>${word.word}</h1>")

    if (word.origin?.isNotBlank() == true)
        sb.append("<p>Origin: ${word.origin}</p>")

    word.phonetics.filter { it.text != null }.nullIfEmpty()?.let { phonetics ->
        sb.append("<h2>Phonetics</h2>")

        var hasSound = false
        phonetics.filter { it.audio?.endsWith("mp3") == true }.forEach { p ->
            hasSound = true
            sb.append("<p><a href=\"${p.audio}\">${p.text}</a>&nbsp;\uD83D\uDD09</font>&nbsp;")
            sb.append("<small>[").append(p.audio!!.substringAfterLast("/")).append("]</small>")
            sb.append("</p>")
        }
        if (!hasSound) {
            phonetics.forEach { p ->
                sb.append("<p>${p.text}</p>")
            }
        }
    }

    word.meanings.forEach { meaning ->

        sb.append("<h2>${meaning.partOfSpeech}</h2>")

        meaning.definitions.forEach { definition ->

            sb.append("<div style=\"padding-left: 10px;\">")

            sb.append("<p><strong>• ${definition.definition}</strong></p>")

            definition.example.nullIfEmpty()?.let {
                sb.append("<p><i>Example: $it</i></p>")
            }

            definition.synonyms.filter { it.isNotBlank() }.nullIfEmpty()?.let {
                sb.append("<p><i>&nbsp;&nbsp;Synonyms: ${it.joinToString(", ")}</i></p>")
            }

            definition.antonyms.filter { it.isNotBlank() }.nullIfEmpty()?.let {
                sb.append("<p><i>&nbsp;&nbsp;Antonyms: ${it.joinToString( ", " )}</i></p>")
            }

            sb.append("</div>")
        }
    }

    sb.append("</body>")
    sb.append("</html>")

    return sb.toString()
}

data class Phonetic(
    val text: String? = null,
    val audio: String? = null
)

data class MeaningDefinition(
    val definition: String,
    val example: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList()
)

data class Meaning(
    val partOfSpeech: String,
    val definitions: List<MeaningDefinition> = emptyList()
)

data class Word(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<Phonetic> = emptyList(),
    val origin: String? = null,
    val meanings: MutableList<Meaning> = mutableListOf(),
)

data class DefinitionResult(
    val status: EStatut,
    var result: Word? = null,
    val shortDefinition: String? = null
)

enum class EStatut { NOT_FOUND, FOUND }