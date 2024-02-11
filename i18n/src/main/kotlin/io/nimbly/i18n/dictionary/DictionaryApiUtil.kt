package io.nimbly.i18n.dictionary

import com.google.gson.Gson
import com.intellij.util.net.HttpConfigurable
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
    sb.append("<p>Phonetic: ${word.phonetic}</p>")
    sb.append("<p>Origin: ${word.origin}</p>")

    sb.append("<h2>Phonetics</h2>")
    word.phonetics.forEach { phonetic ->
        sb.append("<p>Text: ${phonetic.text}</p>")
        sb.append("<p>Audio: ${phonetic.audio}</p>")
    }

    sb.append("<h2>Meanings</h2>")
    word.meanings.forEach { meaning ->
        sb.append("<h3>Part of speech: ${meaning.partOfSpeech}</h3>")

        meaning.definitions.forEach { definition ->
            sb.append("<p>Definition: ${definition.definition}</p>")
            sb.append("<p>Example: ${definition.example}</p>")

            sb.append("<p>Synonyms:</p>")
            sb.append("<ul>")
            definition.synonyms.forEach { synonym ->
                sb.append("<li>$synonym</li>")
            }
            sb.append("</ul>")

            sb.append("<p>Antonyms:</p>")
            sb.append("<ul>")
            definition.antonyms.forEach { antonym ->
                sb.append("<li>$antonym</li>")
            }
            sb.append("</ul>")
        }
    }

    sb.append("</body>")
    sb.append("</html>")

    return sb.toString()
}

data class Phonetic(
    val text: String,
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
    val meanings: List<Meaning> = emptyList(),
)

data class DefinitionResult(
    val status: EStatut,
    var result: Word? = null,
    val shortDefinition: String? = null
)

enum class EStatut { NOT_FOUND, FOUND }