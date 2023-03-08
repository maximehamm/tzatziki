package io.nimbly.tzatziki.generation

import cucumber.runtime.snippets.Snippet

class KotlinSnippet : Snippet {

    override fun arguments(argumentTypes: List<Class<*>>): String {
        val result = StringBuilder()
        argumentTypes.indices.forEach { i ->
            if (i > 0) {
                result.append(", ")
            }
            result.append(" arg")
                .append(i)
                .append(": ")
                .append(getArgType(argumentTypes[i]))
        }
        return result.toString()
    }

    fun getArgType(arg: Class<*>): String {
        return arg.kotlin.simpleName
            ?: arg.simpleName
    }

    override fun template(): String {
        return """
            @{0}("{1}")
            fun {2}({3}) '{'
                
            '}'
            """.trimIndent()
    }

    override fun tableHint(): String {
        return ""
    }

    override fun namedGroupStart(): String? {
        return null
    }

    override fun namedGroupEnd(): String? {
        return null
    }

    override fun escapePattern(pattern: String): String {
        return pattern.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
