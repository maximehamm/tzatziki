/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

/**
 * Pure, IntelliJ-free engine for the "synchronised step rename" feature (#8).
 *
 * A step definition pattern is a *template* made of fixed **literal** text and **parameter**
 * placeholders. A Gherkin step is one instantiation of that template (literals + concrete values).
 * "Renaming a step" = changing the **literal** text only; the parameters (and each sibling step's
 * own values) must be preserved.
 *
 * Two pattern flavours are supported:
 *  - [StepPatternKind.BRACED]  — `{...}` placeholders. Covers cucumber-expressions
 *    (`I have {int} cukes`) AND behave's parse syntax (`I have {count:d} cukes`): the token inside
 *    the braces is kept verbatim, so its dialect is irrelevant for renaming.
 *  - [StepPatternKind.REGEX]   — capturing groups are the parameters (`I have (\d+) cukes`).
 *
 * Everything here is deterministic and side-effect free → unit-testable without the IDE. The IDE
 * layer (per-language pattern rewrite + Gherkin edits + UX) is built on top of this.
 */
enum class StepPatternKind { BRACED, REGEX }

/** A step-definition pattern read from source: its raw text + detected flavour. */
data class StepPatternInfo(val raw: String, val kind: StepPatternKind) {
    companion object {
        /** Heuristic shared by all languages: a pattern anchored with `^`/`$` is treated as a
         *  regex (mirrors cucumber-jvm's own ExpressionFactory heuristic); otherwise it is a
         *  braced cucumber-expression / behave-parse pattern. */
        fun of(raw: String): StepPatternInfo =
            if (raw.startsWith("^") || raw.endsWith("$")) StepPatternInfo(raw, StepPatternKind.REGEX)
            else StepPatternInfo(raw, StepPatternKind.BRACED)
    }
}

sealed interface StepSegment {
    /** Fixed text. For BRACED this is the human text; for REGEX it is raw regex source. */
    data class Literal(val text: String) : StepSegment
    /** A parameter placeholder kept verbatim: `{int}`, `{count:d}`, `(\d+)`, … */
    data class Parameter(val token: String) : StepSegment
}

/** A step-definition pattern split into alternating literal / parameter segments. */
class StepTemplate(
    val kind: StepPatternKind,
    val segments: List<StepSegment>,
    /** REGEX only: whether the original pattern was anchored with `^` / `$`. */
    val regexAnchoredStart: Boolean = false,
    val regexAnchoredEnd: Boolean = false,
) {

    val parameterCount: Int = segments.count { it is StepSegment.Parameter }

    /** Literal segments, in order. There is always `parameterCount + 1` of them (some may be ""). */
    val literals: List<String> = buildList {
        var pendingLiteral = ""
        var sawAny = false
        for (s in segments) when (s) {
            is StepSegment.Literal -> { pendingLiteral += s.text; sawAny = true }
            is StepSegment.Parameter -> { add(pendingLiteral); pendingLiteral = ""; sawAny = true }
        }
        if (sawAny || isEmpty()) add(pendingLiteral)
    }

    /** Parameter tokens, in order (`{int}`, `(\d+)`, …). */
    val parameters: List<String> = segments.filterIsInstance<StepSegment.Parameter>().map { it.token }
}

data class RenameResult(
    /** The rewritten step-definition pattern (params preserved, literals renamed). */
    val newPattern: String,
    /** The rewritten sibling Gherkin steps, in the same order as the input (each value preserved). */
    val newSiblings: List<String>,
)

object StepRenameEngine {

    // --- public API ---------------------------------------------------------

    /** Segment [pattern] into a [StepTemplate], or `null` if it uses constructs we don't safely
     *  handle in V1 (cuke alternation `/`, optional `(text)`, nested/named regex groups…). */
    fun segment(pattern: String, kind: StepPatternKind): StepTemplate? = when (kind) {
        StepPatternKind.BRACED -> segmentBraced(pattern)
        StepPatternKind.REGEX -> segmentRegex(pattern)
    }

    /** Returns the concrete parameter values of [stepText] for [pattern], or `null` if [stepText]
     *  does not match the pattern (e.g. a value, not a literal, was changed). */
    fun valuesOf(pattern: String, kind: StepPatternKind, stepText: String): List<String>? {
        val template = segment(pattern, kind) ?: return null
        return valuesOf(template, stepText)
    }

    /**
     * Compute the synchronised rename.
     *
     * Given the step-definition [pattern], the step being edited (its [oldStepText] BEFORE the edit
     * and [newStepText] AFTER), and the other Gherkin [siblings] bound to the same definition,
     * returns the rewritten pattern + rewritten siblings — or `null` when the rename can't be applied
     * safely (old text doesn't match the pattern, a parameter *value* was changed rather than a
     * literal, ambiguous anchoring, …). In the `null` case the caller simply shows no suggestion.
     */
    fun rename(
        pattern: String,
        kind: StepPatternKind,
        oldStepText: String,
        newStepText: String,
        siblings: List<String> = emptyList(),
    ): RenameResult? {
        val template = segment(pattern, kind) ?: return null

        // 1) Values of the edited step BEFORE the edit — must match the pattern.
        val oldValues = valuesOf(template, oldStepText) ?: return null

        // 2) Derive the NEW literals from the new text, using the (unchanged) values as anchors.
        //    deriveNewLiterals also rejects value edits (a value abutting a literal as a continuous
        //    token), so "1"→"122" bails while inserting a literal between params is allowed.
        //    If a value can't be located in order, the user changed a value (not a literal) → bail.
        val newLiterals = deriveNewLiterals(newStepText, oldValues) ?: return null
        if (newLiterals.size != template.literals.size) return null

        // 3) Rebuild the pattern with the new literals, keeping the parameter tokens verbatim.
        val newPattern = renderPattern(template, newLiterals)

        // 4) Rewrite every sibling: keep its own values, swap in the new literals.
        val newSiblings = siblings.map { sib ->
            val w = valuesOf(template, sib) ?: return@map sib   // unmatched sibling: leave untouched
            instantiate(newLiterals, w)
        }

        return RenameResult(newPattern, newSiblings)
    }

    // --- matching -----------------------------------------------------------

    /** A Scenario-Outline placeholder, e.g. `<count>`. */
    private val OUTLINE_PLACEHOLDER = Regex("<[^>]+>")

    private fun valuesOf(template: StepTemplate, stepText: String): List<String>? {
        // A Scenario-Outline step carries `<placeholders>` in the parameter slots instead of concrete
        // values. A REGEX parameter (e.g. `(\d+)`) would reject `<by>`, so relax REGEX params to
        // "any value" when the step is an outline instantiation (BRACED already matches any value).
        val regex = matchingRegex(template, relaxRegexParams = OUTLINE_PLACEHOLDER.containsMatchIn(stepText))
        val m = regex.matchEntire(stepText) ?: return null
        return m.groupValues.drop(1)
    }

    /** Build an anchored regex that matches a step against [template] and captures each parameter. */
    private fun matchingRegex(template: StepTemplate, relaxRegexParams: Boolean = false): Regex {
        val sb = StringBuilder("^")
        for (s in template.segments) when (s) {
            is StepSegment.Literal ->
                sb.append(if (template.kind == StepPatternKind.REGEX) s.text else Regex.escape(s.text))
            is StepSegment.Parameter ->
                // BRACED: any value. REGEX: reuse the group source so its own constraints apply —
                // unless relaxed (outline step), where the value is a `<placeholder>`, not a match.
                sb.append(if (template.kind == StepPatternKind.REGEX && !relaxRegexParams) s.token else "(.+?)")
        }
        sb.append("$")
        return Regex(sb.toString())
    }

    // --- rename helpers -----------------------------------------------------

    /** From [newText] and the ordered (unchanged) [values], extract the new literal segments
     *  (the text around each value). `null` if the values aren't found in order. */
    private fun deriveNewLiterals(newText: String, values: List<String>): List<String>? {
        if (values.isEmpty()) return listOf(newText)
        val sb = StringBuilder("^")
        for (v in values) { sb.append("(.*?)").append(Regex.escape(v)) }
        sb.append("(.*?)$")
        val literals = (Regex(sb.toString()).matchEntire(newText) ?: return null).groupValues.drop(1)

        // Reject if a value abuts an adjacent literal as a CONTINUOUS alphanumeric token: that means
        // the user changed the parameter VALUE (e.g. "1"→"122" anchors on the first "1" and leaves a
        // bogus "22" literal), not the literals. Inserting whole-token literals between/around values
        // (bounded by spaces, quotes, '<', …) is fine and accepted.
        for (k in values.indices) {
            val v = values[k]
            if (v.isEmpty()) continue
            val left = literals[k]
            val right = literals[k + 1]
            if (left.isNotEmpty() && left.last().isLetterOrDigit() && v.first().isLetterOrDigit()) return null
            if (right.isNotEmpty() && right.first().isLetterOrDigit() && v.last().isLetterOrDigit()) return null
        }
        return literals
    }

    /** Rebuild the step-definition pattern: literals (re-escaped for the flavour) + param tokens. */
    private fun renderPattern(template: StepTemplate, newLiterals: List<String>): String {
        val params = template.parameters
        val sb = StringBuilder()
        if (template.kind == StepPatternKind.REGEX && template.regexAnchoredStart) sb.append("^")
        for (i in newLiterals.indices) {
            sb.append(if (template.kind == StepPatternKind.REGEX) escapeRegexLiteral(newLiterals[i]) else escapeBraced(newLiterals[i]))
            if (i < params.size) sb.append(params[i])
        }
        if (template.kind == StepPatternKind.REGEX && template.regexAnchoredEnd) sb.append("$")
        return sb.toString()
    }

    /** Rebuild a Gherkin step (plain text): new literals + the step's own (plain) values. */
    private fun instantiate(literals: List<String>, values: List<String>): String {
        val sb = StringBuilder()
        for (i in literals.indices) {
            sb.append(literals[i])
            if (i < values.size) sb.append(values[i])
        }
        return sb.toString()
    }

    // --- segmentation -------------------------------------------------------

    private fun segmentBraced(pattern: String): StepTemplate? {
        val segments = mutableListOf<StepSegment>()
        val literal = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' && i + 1 < pattern.length -> { literal.append(pattern[i + 1]); i += 2 }
                // Unsupported-in-V1 cuke constructs → bail rather than corrupt them.
                c == '/' || c == '(' || c == ')' -> return null
                c == '{' -> {
                    val end = pattern.indexOf('}', i)
                    if (end < 0) return null
                    if (literal.isNotEmpty()) { segments.add(StepSegment.Literal(literal.toString())); literal.clear() }
                    segments.add(StepSegment.Parameter(pattern.substring(i, end + 1)))
                    i = end + 1
                }
                else -> { literal.append(c); i++ }
            }
        }
        if (literal.isNotEmpty()) segments.add(StepSegment.Literal(literal.toString()))
        return StepTemplate(StepPatternKind.BRACED, segments)
    }

    private fun segmentRegex(pattern: String): StepTemplate? {
        var body = pattern
        var prefix = ""
        var suffix = ""
        if (body.startsWith("^")) { prefix = "^"; body = body.substring(1) }
        if (body.endsWith("$") && !body.endsWith("\\$")) { suffix = "$"; body = body.dropLast(1) }

        val segments = mutableListOf<StepSegment>()
        val literal = StringBuilder()
        var i = 0
        var depth = 0
        var groupStart = -1
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> { if (depth == 0) literal.append(c).append(body[i + 1]); i += 2 }
                c == '(' -> {
                    if (depth == 0) {
                        // Only top-level CAPTURING groups are parameters; "(?:...)" etc. aren't supported in V1.
                        if (i + 1 < body.length && body[i + 1] == '?') return null
                        if (literal.isNotEmpty()) { segments.add(StepSegment.Literal(literal.toString())); literal.clear() }
                        groupStart = i
                    }
                    depth++; i++
                }
                c == ')' -> {
                    depth--
                    if (depth < 0) return null
                    if (depth == 0 && groupStart >= 0) {
                        segments.add(StepSegment.Parameter(body.substring(groupStart, i + 1)))
                        groupStart = -1
                    }
                    i++
                }
                else -> { if (depth == 0) literal.append(c); i++ }
            }
        }
        if (depth != 0) return null
        if (literal.isNotEmpty()) segments.add(StepSegment.Literal(literal.toString()))

        return StepTemplate(
            StepPatternKind.REGEX, segments,
            regexAnchoredStart = prefix.isNotEmpty(),
            regexAnchoredEnd = suffix.isNotEmpty(),
        )
    }

    // --- escaping -----------------------------------------------------------

    private fun escapeBraced(text: String): String = buildString {
        for (c in text) { if (c == '\\' || c == '{' || c == '}' || c == '(' || c == ')' || c == '/') append('\\'); append(c) }
    }

    private val REGEX_META = setOf('\\', '.', '+', '*', '?', '[', ']', '^', '$', '(', ')', '{', '}', '|')
    private fun escapeRegexLiteral(text: String): String = buildString {
        for (c in text) { if (c in REGEX_META) append('\\'); append(c) }
    }
}
