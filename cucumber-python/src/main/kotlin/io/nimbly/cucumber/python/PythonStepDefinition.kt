/*
 * Cucumber for Python
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * One behave step definition: a top-level Python function decorated with
 * `@given/@when/@then/@step("…")`. [element] is the [PyFunction] itself, so
 * Ctrl+Click on a Gherkin step navigates straight to the function.
 *
 * The base cucumber framework matches steps by REGEX, but behave's default
 * matcher uses the "parse" syntax (`{name:type}`), so we translate the decorator
 * pattern to a regex in [getCucumberRegexFromElement].
 */
class PythonStepDefinition(element: PsiElement) : AbstractStepDefinition(element) {

    override fun getVariableNames(): MutableList<String> = mutableListOf()

    override fun getCucumberRegexFromElement(element: PsiElement?): String? {
        val function = element as? PyFunction ?: return null
        val pattern = behavePatternOf(function) ?: return null
        return behaveToRegex(pattern)
    }

    private fun behavePatternOf(function: PyFunction): String? {
        val decorators = function.decoratorList?.decorators ?: return null
        for (decorator in decorators) {
            val name = decorator.name?.lowercase() ?: continue
            if (name !in STEP_DECORATORS) continue
            val firstArg = decorator.argumentList?.arguments?.firstOrNull()
            val literal = firstArg as? PyStringLiteralExpression ?: continue
            return literal.stringValue
        }
        return null
    }

    /**
     * Convert a behave "parse" pattern to a Java regex:
     *  - literal text is regex-escaped (\Q…\E),
     *  - `{name:d}` → integer capture, `{name:f}`/`g`/`e` → float capture,
     *  - any other `{…}` → a non-greedy `(.+?)` capture.
     * Anchoring is handled by the base matcher (full-match), so no ^…$ here.
     */
    private fun behaveToRegex(pattern: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            if (pattern[i] == '{') {
                val end = pattern.indexOf('}', i)
                if (end < 0) {
                    sb.append(Regex.escape(pattern.substring(i)))
                    break
                }
                val token = pattern.substring(i + 1, end)
                val type = token.substringAfter(':', "").trim()
                sb.append(groupForType(type))
                i = end + 1
            } else {
                val next = pattern.indexOf('{', i)
                val literal = if (next < 0) pattern.substring(i) else pattern.substring(i, next)
                if (literal.isNotEmpty()) sb.append(Regex.escape(literal))
                i += literal.length
            }
        }
        return sb.toString()
    }

    private fun groupForType(type: String): String = when (type) {
        "d" -> "(-?\\d+)"
        "f", "F", "g", "G", "e", "E", "n" -> "(-?\\d+(?:\\.\\d+)?)"
        else -> "(.+?)"
    }

    companion object {
        val STEP_DECORATORS = setOf("given", "when", "then", "step", "and", "but")
    }
}
