package io.nimbly.tzatziki.rename

import io.nimbly.tzatziki.pyRewrittenLiteralText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the Python / behave step rename (#8). Python PSI cannot be parsed in a light fixture
 * (a platform limitation — see the @Ignore'd BreakpointSyncPythonTests), so instead of going through
 * `PyStringLiteralExpression` we unit-test the pure literal-rewriting logic ([pyRewrittenLiteralText])
 * and the kind detection ([StepPatternInfo.of]), which is where the Python-specific risk lives
 * (prefix preservation, raw-string escaping). The PSI navigation is exercised live in the sandbox.
 */
class StepRenamePythonTests {

    // ---- kind detection (behave parse-style vs regex) -----------------------

    @Test fun `parse-style pattern is BRACED`() {
        val info = StepPatternInfo.of("there is {count} cocktails in the order")
        assertEquals("there is {count} cocktails in the order", info.raw)
        assertEquals(StepPatternKind.BRACED, info.kind)
    }

    @Test fun `anchored regex pattern is REGEX`() {
        assertEquals(StepPatternKind.REGEX, StepPatternInfo.of("""^I have (\d+) cukes$""").kind)
    }

    // ---- literal rewriting --------------------------------------------------

    @Test fun `rewrite keeps single quotes`() {
        assertEquals(
            "'there is {count} cocktail in the order'",
            pyRewrittenLiteralText("'there is {count} cocktails in the order'", "there is {count} cocktail in the order"),
        )
    }

    @Test fun `rewrite keeps double quotes`() {
        assertEquals("\"a common step\"", pyRewrittenLiteralText("\"a shared step\"", "a common step"))
    }

    @Test fun `rewrite preserves the u prefix`() {
        assertEquals("u'a common step'", pyRewrittenLiteralText("u'a shared step'", "a common step"))
    }

    @Test fun `raw r-string keeps backslashes un-escaped and preserves prefix`() {
        // behave `re` matcher: r'^…$' — backslashes must NOT be doubled.
        assertEquals(
            """r'^I have (\d+) cucumbers$'""",
            pyRewrittenLiteralText("""r'^I have (\d+) cukes$'""", """^I have (\d+) cucumbers$"""),
        )
    }

    @Test fun `non-raw string escapes backslash and the quote`() {
        assertEquals(
            """'a \\ and a \' quote'""",
            pyRewrittenLiteralText("'x'", """a \ and a ' quote"""),
        )
    }

    @Test fun `no quote returns null`() {
        assertNull(pyRewrittenLiteralText("not a literal", "x"))
    }
}
