package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import io.nimbly.tzatziki.AbstractKotlinTestCase
import io.nimbly.tzatziki.util.findBreakpoint
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.isCucumberSyncBreakpoint
import io.nimbly.tzatziki.util.toggleGherkinBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * End-to-end integration tests for the Gherkin <-> Kotlin code breakpoint synchronization.
 * Mirror of [BreakpointSyncJavaTests] with a Kotlin step-definition class. See
 * tests/TODO-breakpoint-sync-tests.md.
 */
class BreakpointSyncKotlinTests : AbstractKotlinTestCase() {

    private lateinit var ktVFile: VirtualFile

    // ---- creation -----------------------------------------------------------

    fun testGherkinAdd_createsCodeBp_andDoesNotPropagateToSibling() {
        setup()
        val shared = sharedSteps()
        toggleGherkin(shared[0])
        waitFor("synced code breakpoint not created") { syncCodeBps().isNotEmpty() }

        assertEquals(1, syncCodeBps().size)
        assertNotNull(shared[0].findBreakpoint())
        assertNull("sibling step must NOT get a Gherkin BP", shared[1].findBreakpoint())
    }

    fun testCodeAdd_propagatesGherkinBpToAllSharedSteps() {
        setup()
        val shared = sharedSteps()
        toggleCodeBreakpoint(bodyLine("shared"))
        waitFor("Gherkin BPs not propagated to both shared steps") {
            shared[0].findBreakpoint() != null && shared[1].findBreakpoint() != null
        }
        assertEquals(1, syncCodeBps().size)
    }

    // ---- removal ------------------------------------------------------------

    fun testGherkinRemove_singleRef_removesCodeBp() {
        setup()
        val unique = uniqueStep()
        toggleGherkin(unique)
        waitFor("code BP not created for unique step") { syncCodeBpsAt(bodyLine("unique")).isNotEmpty() }

        removeBreakpoint(unique.findBreakpoint()!!)
        waitFor("code BP not removed after deleting the sole Gherkin BP") {
            syncCodeBpsAt(bodyLine("unique")).isEmpty()
        }
    }

    fun testGherkinRemove_sharedWithSibling_keepsCodeBp() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        removeBreakpoint(shared[0].findBreakpoint()!!)
        waitFor("sibling Gherkin BP should remain") { shared[1].findBreakpoint() != null }

        assertEquals(1, syncCodeBpsAt(bodyLine("shared")).size)
    }

    // ---- mute / unmute ------------------------------------------------------

    fun testMuteOneSharedGherkin_keepsCodeEnabled_siblingUnchanged_partial() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        settle()

        assertFalse(shared[0].findBreakpoint()!!.isEnabled)
        assertTrue("sibling must stay enabled", shared[1].findBreakpoint()!!.isEnabled)
        assertTrue("code BP must stay enabled", codeBp()!!.isEnabled)
        assertTrue("mixed state must be partial", isPartial())
    }

    fun testMuteAllSharedGherkin_disablesCodeBp_notPartial() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        setEnabled(shared[1].findBreakpoint()!!, false)
        waitFor("code BP not disabled once all linked steps muted") { codeBp()?.isEnabled == false }
        assertFalse(isPartial())
    }

    fun testUnmuteOneFromAllMuted_reEnablesCodeBp_siblingsStayMuted_partial() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        setEnabled(shared[1].findBreakpoint()!!, false)
        waitFor("code BP not disabled") { codeBp()?.isEnabled == false }

        setEnabled(shared[0].findBreakpoint()!!, true)
        waitFor("code BP not re-enabled after unmuting one step") { codeBp()?.isEnabled == true }

        assertFalse("sibling must stay muted", shared[1].findBreakpoint()!!.isEnabled)
        assertTrue("mixed state must be partial", isPartial())
    }

    fun testMuteCodeBp_propagatesToAllSharedGherkinSteps() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(codeBp()!!, false)
        waitFor("muting the code BP must mute all linked Gherkin steps") {
            shared[0].findBreakpoint()?.isEnabled == false && shared[1].findBreakpoint()?.isEnabled == false
        }
    }

    // ----- fixture -----------------------------------------------------------

    private fun setup() {
        // language=kotlin
        configure(
            """
            package io.nimbly
            import io.cucumber.java.en.Given
            import java.lang.Boolean
            class Steps {
                @Given("a shared step")
                fun shared() {
                    println("shared")
                }
                @Given("a unique step")
                fun unique() {
                    println("unique")
                }
            }""",
        )
        ktVFile = configuredFile!!.virtualFile
        // language=feature
        feature(
            """
            Feature: F
              Scenario: One
                Given a shared step
                Given a unique step
              Scenario: Two
                Given a shared step
            """,
        )
    }

    // ----- helpers -----------------------------------------------------------

    private fun gherkinFile(): GherkinFile = configuredFile as GherkinFile

    private fun steps(): List<GherkinStep> =
        PsiTreeUtil.findChildrenOfType(gherkinFile(), GherkinStep::class.java).toList()

    private fun sharedSteps(): List<GherkinStep> = steps().filter { it.text.contains("a shared step") }
    private fun uniqueStep(): GherkinStep = steps().first { it.text.contains("a unique step") }

    private fun syncCodeBps(): List<XBreakpoint<*>> =
        XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
            .filter { it.sourcePosition?.file == ktVFile && it.isCucumberSyncBreakpoint() }

    private fun syncCodeBpsAt(line: Int): List<XBreakpoint<*>> =
        syncCodeBps().filter { it.sourcePosition?.line == line }

    private fun bodyLine(marker: String): Int {
        val doc = FileDocumentManager.getInstance().getDocument(ktVFile)!!
        val idx = doc.charsSequence.toString().indexOf("\"$marker\"")
        return doc.getLineNumber(idx)
    }

    private fun toggleGherkin(step: GherkinStep) {
        val line = step.getDocumentLine() ?: error("step has no document line")
        WriteCommandAction.runWriteCommandAction(project) { step.toggleGherkinBreakpoint(line) }
    }

    private fun toggleCodeBreakpoint(line: Int) {
        WriteCommandAction.runWriteCommandAction(project) {
            XDebuggerUtil.getInstance().toggleLineBreakpoint(project, ktVFile, line)
        }
    }

    private fun removeBreakpoint(bp: XBreakpoint<*>) {
        WriteCommandAction.runWriteCommandAction(project) { XDebuggerUtil.getInstance().removeBreakpoint(project, bp) }
    }

    private fun setEnabled(bp: XBreakpoint<*>, enabled: Boolean) {
        WriteCommandAction.runWriteCommandAction(project) { bp.isEnabled = enabled }
    }

    private fun codeBp(): XBreakpoint<*>? = syncCodeBpsAt(bodyLine("shared")).firstOrNull()

    private fun isPartial(): Boolean = TzPartialMutePresentation.recompute(project).any { it.value }

    private fun breakpointBothShared(shared: List<GherkinStep>) {
        toggleCodeBreakpoint(bodyLine("shared"))
        waitFor("both shared steps not breakpointed") {
            shared[0].findBreakpoint() != null && shared[1].findBreakpoint() != null
        }
    }

    private fun waitFor(message: String, condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching(message, condition, 15)

    private fun settle() {
        val deadline = System.currentTimeMillis() + 2500
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(25)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
}
