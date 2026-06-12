package io.nimbly.tzatziki.rename

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.AbstractJavaTestCase
import io.nimbly.tzatziki.JavaTzatzikiExtensionPoint

/**
 * Fixture tests for the Java side of the synchronised step rename (#8):
 * [JavaTzatzikiExtensionPoint.getStepPattern] / [JavaTzatzikiExtensionPoint.rewriteStepPattern].
 */
class StepRenameJavaTests : AbstractJavaTestCase() {

    private val ep = JavaTzatzikiExtensionPoint()

    private fun method(name: String): PsiMethod =
        PsiTreeUtil.findChildrenOfType(configuredFile, PsiMethod::class.java).first { it.name == name }

    fun testReadBracedPattern() {
        setup()
        val info = ep.getStepPattern(method("shared"))
        assertNotNull(info)
        assertEquals("a shared step", info!!.raw)
        assertEquals(StepPatternKind.BRACED, info.kind)
    }

    fun testReadRegexPatternKind() {
        setup()
        val info = ep.getStepPattern(method("counted"))
        assertNotNull(info)
        assertEquals(StepPatternKind.REGEX, info!!.kind)
        assertEquals("""^I have (\d+) cukes$""", info.raw)
    }

    fun testRewriteBracedPattern() {
        setup()
        val m = method("shared")
        WriteCommandAction.runWriteCommandAction(project) {
            assertTrue(ep.rewriteStepPattern(m, "a common step"))
        }
        assertEquals("a common step", ep.getStepPattern(method("shared"))!!.raw)
    }

    fun testRewriteRegexPatternEscapesBackslashes() {
        setup()
        val m = method("counted")
        WriteCommandAction.runWriteCommandAction(project) {
            assertTrue(ep.rewriteStepPattern(m, """^I have (\d+) cucumbers$"""))
        }
        assertEquals("""^I have (\d+) cucumbers$""", ep.getStepPattern(method("counted"))!!.raw)
    }

    private fun setup() {
        // language=Java
        configure(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("a shared step")
                public void shared() {}
                @io.cucumber.java.en.Given("^I have (\\d+) cukes$")
                public void counted() {}
            }""",
        )
    }
}
