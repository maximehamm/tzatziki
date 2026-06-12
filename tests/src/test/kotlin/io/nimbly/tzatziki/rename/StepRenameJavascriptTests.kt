package io.nimbly.tzatziki.rename

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.AbstractJavascriptTestCase
import io.nimbly.tzatziki.JsTzatzikiExtensionPoint

/**
 * Fixture tests for the JavaScript / TypeScript side of the synchronised step rename (#8):
 * [JsTzatzikiExtensionPoint.getStepPattern] / [JsTzatzikiExtensionPoint.rewriteStepPattern], for both
 * cucumber-expression **string** literals and **regex** literals.
 */
class StepRenameJavascriptTests : AbstractJavascriptTestCase() {

    private val ep = JsTzatzikiExtensionPoint()

    private fun literals(): List<JSLiteralExpression> =
        PsiTreeUtil.findChildrenOfType(configuredFile, JSLiteralExpression::class.java).toList()

    private fun stringLiteral(contains: String): JSLiteralExpression =
        literals().first { it.isStringLiteral && it.text.contains(contains) }

    private fun regexLiteral(): JSLiteralExpression =
        literals().first { it.isRegExpLiteral }

    fun testReadStringPattern() {
        setup()
        val info = ep.getStepPattern(stringLiteral("a shared step"))!!
        assertEquals("a shared step", info.raw)
        assertEquals(StepPatternKind.BRACED, info.kind)
    }

    fun testReadRegexPattern() {
        setup()
        val info = ep.getStepPattern(regexLiteral())!!
        assertEquals("""^I have (\d+) cukes$""", info.raw)
        assertEquals(StepPatternKind.REGEX, info.kind)
    }

    fun testRewriteStringPatternKeepsQuotes() {
        setup()
        val lit = stringLiteral("a shared step")
        WriteCommandAction.runWriteCommandAction(project) { assertTrue(ep.rewriteStepPattern(lit, "a common step")) }
        assertTrue(configuredFile!!.text.contains("'a common step'"))
        assertFalse(configuredFile!!.text.contains("a shared step"))
    }

    fun testRewriteRegexKeepsSlashesAndKind() {
        setup()
        val lit = regexLiteral()
        WriteCommandAction.runWriteCommandAction(project) {
            assertTrue(ep.rewriteStepPattern(lit, """^I have (\d+) cucumbers$"""))
        }
        assertTrue(configuredFile!!.text.contains("""/^I have (\d+) cucumbers$/"""))
        assertEquals(StepPatternKind.REGEX, ep.getStepPattern(regexLiteral())!!.kind)
    }

    /** TypeScript uses the same JS PSI ([JSLiteralExpression]) → the same EP handles it. */
    fun testTypescriptStringAndRegex() {
        myFixture.configureByText(
            "steps.ts",
            """
            import { Given } from '@cucumber/cucumber'
            Given('a shared step', (): void => {})
            Given(/^I have (\d+) cukes$/, (n: number): void => {})
            """.trimIndent(),
        )
        val lits = PsiTreeUtil.findChildrenOfType(myFixture.file, JSLiteralExpression::class.java).toList()
        val str = lits.first { it.isStringLiteral && it.text.contains("a shared step") }
        val rgx = lits.first { it.isRegExpLiteral }
        assertEquals("a shared step", ep.getStepPattern(str)!!.raw)
        assertEquals(StepPatternKind.BRACED, ep.getStepPattern(str)!!.kind)
        assertEquals("""^I have (\d+) cukes$""", ep.getStepPattern(rgx)!!.raw)
        assertEquals(StepPatternKind.REGEX, ep.getStepPattern(rgx)!!.kind)
        WriteCommandAction.runWriteCommandAction(project) { assertTrue(ep.rewriteStepPattern(str, "a common step")) }
        assertTrue(myFixture.file.text.contains("'a common step'"))
    }

    private fun setup() {
        configure(
            """
            const { Given } = require('@cucumber/cucumber');
            Given('a shared step', function () {});
            Given(/^I have (\d+) cukes$/, function () {});
            """.trimIndent(),
        )
    }
}
