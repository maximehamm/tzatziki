package io.nimbly.tzatziki.rename

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [shouldSuggestRename] — the proactive "Rename steps and references…" inlay must appear
 * when the step NAME is edited, but NOT when the whole step line is deleted (the reported bug:
 * selecting the line + Delete wrongly showed the inlay).
 */
class StepRenameSuggesterTest {

    private val original = "    Given a calculator with value 10"
    private val nameStart = original.indexOf("a calculator")   // right after "Given "

    @Test fun `editing the step name shows the suggestion`() {
        assertTrue(shouldSuggestRename(original, nameStart, "    Given a calculator with the value of 10"))
    }

    @Test fun `deleting the whole line (blank) does NOT suggest`() {
        assertFalse(shouldSuggestRename(original, nameStart, ""))
        assertFalse(shouldSuggestRename(original, nameStart, "      "))
    }

    @Test fun `deleting the keyword does NOT suggest`() {
        assertFalse(shouldSuggestRename(original, nameStart, "    a calculator with value 10"))
    }

    @Test fun `an unrelated next line shifted in does NOT suggest`() {
        // line removed → another step shifts into the snapshot line: different prefix → no suggestion
        assertFalse(shouldSuggestRename(original, nameStart, "    Then the result is 15"))
    }

    @Test fun `an unchanged line does NOT suggest`() {
        assertFalse(shouldSuggestRename(original, nameStart, original))
    }

    @Test fun `a blank prefix never suggests`() {
        assertFalse(shouldSuggestRename("10", 0, "20"))
    }
}
