package io.nimbly.tzatziki.rename

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.AbstractKotlinTestCase
import io.nimbly.tzatziki.KotlinTzatzikiExtensionPoint
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Fixture tests for the Kotlin side of the synchronised step rename (#8):
 * [KotlinTzatzikiExtensionPoint.getStepPattern] / [KotlinTzatzikiExtensionPoint.rewriteStepPattern].
 */
class StepRenameKotlinTests : AbstractKotlinTestCase() {

    private val ep = KotlinTzatzikiExtensionPoint()

    private fun function(name: String): KtNamedFunction =
        PsiTreeUtil.findChildrenOfType(configuredFile, KtNamedFunction::class.java).first { it.name == name }

    fun testReadBracedPattern() {
        setup()
        val info = ep.getStepPattern(function("shared"))
        assertNotNull(info)
        assertEquals("a shared step", info!!.raw)
        assertEquals(StepPatternKind.BRACED, info.kind)
    }

    fun testRewriteBracedPattern() {
        setup()
        WriteCommandAction.runWriteCommandAction(project) {
            assertTrue(ep.rewriteStepPattern(function("shared"), "a common step"))
        }
        assertEquals("a common step", ep.getStepPattern(function("shared"))!!.raw)
    }

    fun testRewriteRegexEscapesDollarAndBackslash() {
        setup()
        // A regex with a `$` anchor and a `\d` class — in a Kotlin string both `$` (interpolation)
        // and `\` must be escaped. The pattern must round-trip back to its exact raw value.
        val pattern = "^I have (\\d+) cukes" + "\$"
        WriteCommandAction.runWriteCommandAction(project) {
            assertTrue(ep.rewriteStepPattern(function("shared"), pattern))
        }
        val info = ep.getStepPattern(function("shared"))!!
        assertEquals(pattern, info.raw)
        assertEquals(StepPatternKind.REGEX, info.kind)
    }

    private fun setup() {
        configure(
            """
            package io.nimbly
            class Steps {
                @io.cucumber.java.en.Given("a shared step") fun shared() {}
            }
            """.trimIndent(),
        )
    }
}
