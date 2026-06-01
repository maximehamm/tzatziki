package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.nimbly.tzatziki.AbstractJavaTestCase
import io.nimbly.tzatziki.util.findBreakpoint
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.isCucumberSyncBreakpoint
import io.nimbly.tzatziki.util.toggleGherkinBreakpoint
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * End-to-end integration tests for the Gherkin <-> Java code breakpoint synchronization
 * (see tests/TODO-breakpoint-sync-tests.md). Drives the real (asynchronous) TzBreakpointListener
 * and waits for the expected breakpoint-manager state via PlatformTestUtil.waitWithEventsDispatching.
 *
 * Fixture: two scenarios. "a shared step" appears twice -> ONE step definition shared by two
 * Gherkin steps (sibling context). "a unique step" appears once -> single reference.
 */
class BreakpointSyncJavaTests : AbstractJavaTestCase() {

    private lateinit var javaVFile: VirtualFile

    // ---- creation -----------------------------------------------------------

    fun testGherkinAdd_createsCodeBp_andDoesNotPropagateToSibling() {
        setup()
        val shared = sharedSteps()
        toggleGherkin(shared[0])

        waitFor("synced code breakpoint not created") { syncCodeBps().isNotEmpty() }

        assertEquals("exactly one shared code breakpoint expected", 1, syncCodeBps().size)
        assertNotNull("the breakpointed step keeps its Gherkin BP", shared[0].findBreakpoint())
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

        // remove the Gherkin breakpoint
        val gbp = unique.findBreakpoint()!!
        removeBreakpoint(gbp)

        waitFor("code BP not removed after deleting the sole Gherkin BP") {
            syncCodeBpsAt(bodyLine("unique")).isEmpty()
        }
    }

    fun testGherkinRemove_sharedWithSibling_keepsCodeBp() {
        setup()
        val shared = sharedSteps()
        // both steps breakpointed -> code BP created; both Gherkin BPs present
        toggleCodeBreakpoint(bodyLine("shared"))
        waitFor("both shared steps not breakpointed") {
            shared[0].findBreakpoint() != null && shared[1].findBreakpoint() != null
        }

        // remove ONE Gherkin BP -> code BP must stay (the other step still references it)
        removeBreakpoint(shared[0].findBreakpoint()!!)
        waitFor("sibling Gherkin BP should remain") { shared[1].findBreakpoint() != null }

        assertEquals("shared code BP must survive while a sibling step still references it",
            1, syncCodeBpsAt(bodyLine("shared")).size)
    }

    // ---- mute / unmute ------------------------------------------------------

    fun testMuteOneSharedGherkin_keepsCodeEnabled_siblingUnchanged_partial() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        // mute ONE of the two shared steps
        setEnabled(shared[0].findBreakpoint()!!, false)
        settle()

        assertFalse("muted step BP must be disabled", shared[0].findBreakpoint()!!.isEnabled)
        assertTrue("sibling step BP must stay enabled (no propagation)", shared[1].findBreakpoint()!!.isEnabled)
        assertTrue("shared code BP must stay enabled (a step is still active)", codeBp()!!.isEnabled)
        assertTrue("mixed state must be reported as partial", isPartial())
    }

    fun testMuteAllSharedGherkin_disablesCodeBp_notPartial() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        setEnabled(shared[1].findBreakpoint()!!, false)

        waitFor("code BP not disabled once all linked steps are muted") { codeBp()?.isEnabled == false }
        assertFalse("uniform (all muted) state must NOT be partial", isPartial())
    }

    fun testUnmuteOneFromAllMuted_reEnablesCodeBp_siblingsStayMuted_partial() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        // mute both -> code BP disabled
        setEnabled(shared[0].findBreakpoint()!!, false)
        setEnabled(shared[1].findBreakpoint()!!, false)
        waitFor("code BP not disabled") { codeBp()?.isEnabled == false }

        // unmute only one
        setEnabled(shared[0].findBreakpoint()!!, true)
        waitFor("code BP not re-enabled after unmuting one step") { codeBp()?.isEnabled == true }

        assertFalse("sibling must stay muted (no propagation on unmute)", shared[1].findBreakpoint()!!.isEnabled)
        assertTrue("mixed state must be reported as partial", isPartial())
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
        // language=Java
        configure(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("a shared step")
                public void shared() {
                    System.out.println("shared");
                }
                @io.cucumber.java.en.Given("a unique step")
                public void unique() {
                    System.out.println("unique");
                }
            }""",
        )
        javaVFile = configuredFile!!.virtualFile
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
            .filter { it.sourcePosition?.file == javaVFile && it.isCucumberSyncBreakpoint() }

    private fun syncCodeBpsAt(line: Int): List<XBreakpoint<*>> =
        syncCodeBps().filter { it.sourcePosition?.line == line }

    /** Document line of the `System.out.println("<marker>")` body statement. */
    private fun bodyLine(marker: String): Int {
        val doc = FileDocumentManager.getInstance().getDocument(javaVFile)!!
        val text = doc.charsSequence.toString()
        val idx = text.indexOf("\"$marker\"")
        return doc.getLineNumber(idx)
    }

    private fun toggleGherkin(step: GherkinStep) {
        val line = step.getDocumentLine() ?: error("step has no document line")
        WriteCommandAction.runWriteCommandAction(project) { step.toggleGherkinBreakpoint(line) }
    }

    private fun toggleCodeBreakpoint(line: Int) {
        WriteCommandAction.runWriteCommandAction(project) {
            XDebuggerUtil.getInstance().toggleLineBreakpoint(project, javaVFile, line)
        }
    }

    private fun removeBreakpoint(bp: XBreakpoint<*>) {
        WriteCommandAction.runWriteCommandAction(project) {
            XDebuggerUtil.getInstance().removeBreakpoint(project, bp)
        }
    }

    private fun setEnabled(bp: XBreakpoint<*>, enabled: Boolean) {
        WriteCommandAction.runWriteCommandAction(project) { bp.isEnabled = enabled }
    }

    /** The single shared code breakpoint (on the `shared()` body line), or null. */
    private fun codeBp(): XBreakpoint<*>? = syncCodeBpsAt(bodyLine("shared")).firstOrNull()

    /** True when any synced code breakpoint is in the mixed "partial mute" state. */
    private fun isPartial(): Boolean = TzPartialMutePresentation.recompute(project).any { it.value }

    /** Breakpoint both shared steps by adding the code BP (which propagates to all). */
    private fun breakpointBothShared(shared: List<GherkinStep>) {
        toggleCodeBreakpoint(bodyLine("shared"))
        waitFor("both shared steps not breakpointed") {
            shared[0].findBreakpoint() != null && shared[1].findBreakpoint() != null
        }
    }

    private fun waitFor(message: String, condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching(message, condition, 15)

    /** Drain the async refresh pipeline (debounce + pooled + invokeLater) and let it settle. */
    private fun settle() {
        val deadline = System.currentTimeMillis() + 2500
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(25)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
}
