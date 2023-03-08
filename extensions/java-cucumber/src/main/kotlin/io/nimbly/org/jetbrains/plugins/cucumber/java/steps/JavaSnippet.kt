package io.nimbly.org.jetbrains.plugins.cucumber.java.steps

import cucumber.runtime.snippets.Snippet

class JavaSnippet : Snippet {

    override fun arguments(argumentTypes: List<Class<*>>): String {
        val result = StringBuilder()
        for (i in argumentTypes.indices) {
            val arg = argumentTypes[i]
            if (i > 0) {
                result.append(", ")
            }
            result.append(getArgType(arg)).append(" arg").append(i)
        }
        return result.toString()
    }

    protected fun getArgType(arg: Class<*>): String {
        return arg.simpleName
    }

    override fun template(): String {
        return "@{0}(\"{1}\")\npublic void {2}({3}) throws Throwable '{'\n    // {4}\n{5}    throw new PendingException();\n'}'\n"
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
