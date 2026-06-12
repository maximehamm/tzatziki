package io.nimbly.tzatziki.rename

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for [StepRenameEngine] — the segmentation / match / synchronised-rename core
 * of the "rename a Gherkin step ↔ step definition" feature (#8). No IDE / fixture needed.
 */
class StepRenameEngineTest {

    // ---- segmentation -------------------------------------------------------

    @Test fun `segment braced - literal only`() {
        val t = StepRenameEngine.segment("a shared step", StepPatternKind.BRACED)!!
        assertEquals(0, t.parameterCount)
        assertEquals(listOf("a shared step"), t.literals)
    }

    @Test fun `segment braced - with params`() {
        val t = StepRenameEngine.segment("I have {int} cukes", StepPatternKind.BRACED)!!
        assertEquals(1, t.parameterCount)
        assertEquals(listOf("I have ", " cukes"), t.literals)
        assertEquals(listOf("{int}"), t.parameters)
    }

    @Test fun `segment braced - bails on alternation and optional (V1 limit)`() {
        assertNull(StepRenameEngine.segment("a cuke/cukes", StepPatternKind.BRACED))
        assertNull(StepRenameEngine.segment("I have {int} cuke(s)", StepPatternKind.BRACED))
    }

    @Test fun `segment regex - groups are params`() {
        val t = StepRenameEngine.segment("""I have (\d+) cukes""", StepPatternKind.REGEX)!!
        assertEquals(1, t.parameterCount)
        assertEquals(listOf("I have ", " cukes"), t.literals)
        assertEquals(listOf("""(\d+)"""), t.parameters)
    }

    // ---- matching -----------------------------------------------------------

    @Test fun `valuesOf braced`() {
        assertEquals(listOf("5"), StepRenameEngine.valuesOf("I have {int} cukes", StepPatternKind.BRACED, "I have 5 cukes"))
        assertEquals(emptyList<String>(), StepRenameEngine.valuesOf("a shared step", StepPatternKind.BRACED, "a shared step"))
        assertNull(StepRenameEngine.valuesOf("I have {int} cukes", StepPatternKind.BRACED, "totally different"))
    }

    @Test fun `valuesOf regex`() {
        assertEquals(listOf("42"), StepRenameEngine.valuesOf("""^I have (\d+) cukes$""", StepPatternKind.REGEX, "I have 42 cukes"))
    }

    // ---- rename: literal only ----------------------------------------------

    @Test fun `rename literal-only step renames pattern and siblings`() {
        val r = StepRenameEngine.rename(
            pattern = "a shared step", kind = StepPatternKind.BRACED,
            oldStepText = "a shared step", newStepText = "a common step",
            siblings = listOf("a shared step"),
        )
        assertNotNull(r)
        assertEquals("a common step", r!!.newPattern)
        assertEquals(listOf("a common step"), r.newSiblings)
    }

    // ---- rename: parameterised (params handled in V1) -----------------------

    @Test fun `rename trailing literal keeps the parameter and each sibling value`() {
        val r = StepRenameEngine.rename(
            pattern = "I have {int} cukes", kind = StepPatternKind.BRACED,
            oldStepText = "I have 5 cukes", newStepText = "I have 5 cucumbers",
            siblings = listOf("I have 12 cukes", "I have 0 cukes"),
        )
        assertNotNull(r)
        assertEquals("I have {int} cucumbers", r!!.newPattern)
        assertEquals(listOf("I have 12 cucumbers", "I have 0 cucumbers"), r.newSiblings)
    }

    @Test fun `rename a middle literal`() {
        val r = StepRenameEngine.rename(
            pattern = "I have {int} red {word}", kind = StepPatternKind.BRACED,
            oldStepText = "I have 5 red apples", newStepText = "I have 5 green apples",
            siblings = listOf("I have 3 red pears"),
        )
        assertNotNull(r)
        assertEquals("I have {int} green {word}", r!!.newPattern)
        assertEquals(listOf("I have 3 green pears"), r.newSiblings)
    }

    @Test fun `rename preserves behave parse-style token verbatim`() {
        val r = StepRenameEngine.rename(
            pattern = "I have {count:d} cukes", kind = StepPatternKind.BRACED,
            oldStepText = "I have 5 cukes", newStepText = "I own 5 cukes",
            siblings = emptyList(),
        )
        assertNotNull(r)
        assertEquals("I own {count:d} cukes", r!!.newPattern)
    }

    @Test fun `regex pattern matches a Scenario-Outline placeholder step (relaxed)`() {
        // A REGEX def `^I divide by (\d+)$` with a Scenario-Outline step `I divide by <by>`:
        // the `<by>` placeholder isn't a digit, so the param matching must be relaxed.
        assertEquals(
            listOf("<by>"),
            StepRenameEngine.valuesOf("""^I divide by (\d+)$""", StepPatternKind.REGEX, "I divide by <by>"),
        )
    }

    @Test fun `rename a regex-defined outline step keeps the group, placeholder and concrete siblings`() {
        val r = StepRenameEngine.rename(
            pattern = """^I divide by (\d+)$""", kind = StepPatternKind.REGEX,
            oldStepText = "I divide by <by>", newStepText = "I divide by <by> now",
            siblings = listOf("I divide by 8"),
        )
        assertNotNull(r)
        assertEquals("""^I divide by (\d+) now$""", r!!.newPattern)
        assertEquals(listOf("I divide by 8 now"), r.newSiblings)
    }

    @Test fun `rename regex keeps the group and re-escapes anchors`() {
        val r = StepRenameEngine.rename(
            pattern = """^I have (\d+) cukes$""", kind = StepPatternKind.REGEX,
            oldStepText = "I have 5 cukes", newStepText = "I have 5 cucumbers",
            siblings = listOf("I have 7 cukes"),
        )
        assertNotNull(r)
        assertEquals("""^I have (\d+) cucumbers$""", r!!.newPattern)
        assertEquals(listOf("I have 7 cucumbers"), r.newSiblings)
    }

    // ---- rename: safety bails ----------------------------------------------

    @Test fun `rename bails when a value (not a literal) was changed`() {
        val r = StepRenameEngine.rename(
            pattern = "I have {int} cukes", kind = StepPatternKind.BRACED,
            oldStepText = "I have 5 cukes", newStepText = "I have 6 cukes",   // value, not literal
            siblings = emptyList(),
        )
        assertNull(r)
    }

    @Test fun `rename bails when old text does not match the pattern`() {
        val r = StepRenameEngine.rename(
            pattern = "I have {int} cukes", kind = StepPatternKind.BRACED,
            oldStepText = "unrelated", newStepText = "unrelated step",
            siblings = emptyList(),
        )
        assertNull(r)
    }

    @Test fun `unmatched sibling is left untouched`() {
        val r = StepRenameEngine.rename(
            pattern = "I have {int} cukes", kind = StepPatternKind.BRACED,
            oldStepText = "I have 5 cukes", newStepText = "I have 5 cucumbers",
            siblings = listOf("I have 12 cukes", "something else entirely"),
        )
        assertNotNull(r)
        assertEquals(listOf("I have 12 cucumbers", "something else entirely"), r!!.newSiblings)
    }

    // ---- value vs literal disambiguation (boundary rule) --------------------

    @Test fun `inserting a literal between two parameters keeps both params`() {
        val r = StepRenameEngine.rename(
            pattern = "l'utilisateur {string} a {int} ans", kind = StepPatternKind.BRACED,
            oldStepText = "l'utilisateur \"<Prenom>\" a <Age> ans",
            newStepText = "l'utilisateur \"<Prenom>\" a xxx <Age> ans",
        )
        assertNotNull(r)
        assertEquals("l'utilisateur {string} a xxx {int} ans", r!!.newPattern)
    }

    @Test fun `appending a literal after a trailing parameter is accepted`() {
        val r = StepRenameEngine.rename(
            pattern = "son score devrait être {}", kind = StepPatternKind.BRACED,
            oldStepText = "son score devrait être 92", newStepText = "son score devrait être 92 points",
        )
        assertNotNull(r)
        assertEquals("son score devrait être {} points", r!!.newPattern)
    }

    @Test fun `changing a value to a superset bails (no pattern corruption)`() {
        assertNull(StepRenameEngine.rename("there is {int} cocktails", StepPatternKind.BRACED, "there is 1 cocktails", "there is 122 cocktails"))
        assertNull(StepRenameEngine.rename("son score devrait être {}", StepPatternKind.BRACED, "son score devrait être 92", "son score devrait être 920"))
    }

    @Test fun `reordering parameters is not supported (bails)`() {
        // Reordering params would also require reordering the step-def method parameters (positional
        // binding), which a rename doesn't do → bail rather than produce a broken definition.
        assertNull(
            StepRenameEngine.rename(
                "l'utilisateur {string} a {int} ans", StepPatternKind.BRACED,
                "l'utilisateur \"<Prenom>\" a <Age> ans",
                "l'utilisateur qui a <Age> ans est \"<Prenom>\"",
            ),
        )
    }
}
