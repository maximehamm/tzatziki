package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.nimbly.cucumber.python.CucumberPythonExtension
import io.nimbly.tzatziki.AbstractTestCase
import io.nimbly.tzatziki.PyTzatzikiExtensionPoint
import io.nimbly.tzatziki.util.findBreakpoint
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.toggleGherkinBreakpoint
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.junit.Ignore

/**
 * End-to-end integration tests for the Gherkin <-> Python (behave) code breakpoint
 * synchronization — mirror of [BreakpointSyncJavaTests] / [BreakpointSyncJavascriptTests].
 *
 * Python step-def breakpoints stay the NATIVE `python-line` type (not a Cucumber+ type),
 * so the partial-mute icon does not apply; these tests assert the enabled-state model.
 *
 * ⚠️ @Ignore — BLOCKED by a platform limitation, NOT by the feature:
 *   In a [com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase] the Python
 *   language is not activated: a `.py` file is parsed as `PsiPlainTextFileImpl`
 *   (fileType = PLAIN_TEXT), never as a `PyFile`. Behave step-def discovery
 *   (CucumberPythonExtension.getStepDefinitionContainers / loadStepsFor) therefore finds
 *   nothing, so Gherkin <-> Python resolution and the breakpoint sync cannot run here.
 *   Enabling these requires a Python-aware fixture (e.g. com.jetbrains.python.fixtures.
 *   PyTestCase) that also wires the Gherkin/cucumber stack — not available in this module.
 *   The Python breakpoint sync behaviour is validated manually in the sandbox.
 *
 * The structure below is kept ready to enable once a Python-capable fixture exists.
 */
@Ignore
class BreakpointSyncPythonTests : AbstractTestCase() {

    private lateinit var pyVFile: VirtualFile

    override fun tzatzikiExtensions() = super.tzatzikiExtensions() + PyTzatzikiExtensionPoint()

    override fun setUp() {
        super.setUp()
        val ep = ExtensionPointName<CucumberJvmExtensionPoint>("org.jetbrains.plugins.cucumber.steps.cucumberJvmExtensionPoint")
        ExtensionTestUtil.maskExtensions(ep, ep.extensionList + CucumberPythonExtension(), testRootDisposable)
    }

    // ---- creation -----------------------------------------------------------

    fun testGherkinAdd_createsCodeBp_andDoesNotPropagateToSibling() {
        setup()
        val shared = sharedSteps()
        toggleGherkin(shared[0])
        waitFor("synced Python code breakpoint not created") { codeBps().isNotEmpty() }

        assertEquals(1, codeBps().size)
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
        assertEquals(1, codeBps().size)
    }

    // ---- removal ------------------------------------------------------------

    fun testGherkinRemove_singleRef_removesCodeBp() {
        setup()
        val unique = uniqueStep()
        toggleGherkin(unique)
        waitFor("code BP not created for unique step") { codeBpsAt(bodyLine("unique")).isNotEmpty() }

        removeBreakpoint(unique.findBreakpoint()!!)
        waitFor("code BP not removed after deleting the sole Gherkin BP") {
            codeBpsAt(bodyLine("unique")).isEmpty()
        }
    }

    fun testGherkinRemove_sharedWithSibling_keepsCodeBp() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        removeBreakpoint(shared[0].findBreakpoint()!!)
        waitFor("sibling Gherkin BP should remain") { shared[1].findBreakpoint() != null }

        assertEquals(1, codeBpsAt(bodyLine("shared")).size)
    }

    // ---- mute / unmute ------------------------------------------------------

    fun testMuteOneSharedGherkin_keepsCodeEnabled_siblingUnchanged() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        settle()

        assertFalse(shared[0].findBreakpoint()!!.isEnabled)
        assertTrue("sibling must stay enabled", shared[1].findBreakpoint()!!.isEnabled)
        assertTrue("code BP must stay enabled", codeBp()!!.isEnabled)
    }

    fun testMuteAllSharedGherkin_disablesCodeBp() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        setEnabled(shared[1].findBreakpoint()!!, false)
        waitFor("code BP not disabled once all linked steps muted") { codeBp()?.isEnabled == false }
    }

    fun testUnmuteOneFromAllMuted_reEnablesCodeBp_siblingsStayMuted() {
        setup()
        val shared = sharedSteps()
        breakpointBothShared(shared)

        setEnabled(shared[0].findBreakpoint()!!, false)
        setEnabled(shared[1].findBreakpoint()!!, false)
        waitFor("code BP not disabled") { codeBp()?.isEnabled == false }

        setEnabled(shared[0].findBreakpoint()!!, true)
        waitFor("code BP not re-enabled after unmuting one step") { codeBp()?.isEnabled == true }

        assertFalse("sibling must stay muted", shared[1].findBreakpoint()!!.isEnabled)
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
        // behave convention: step defs under features/steps/*.py next to the .feature files.
        val py = myFixture.addFileToProject(
            "features/steps/steps.py",
            """
            from behave import given

            @given('a shared step')
            def shared(context):
                print("shared")

            @given('a unique step')
            def unique(context):
                print("unique")
            """.trimIndent(),
        )
        pyVFile = py.virtualFile
        configuredFile = myFixture.addFileToProject(
            "features/calc.feature",
            """
            Feature: F
              Scenario: One
                Given a shared step
                Given a unique step
              Scenario: Two
                Given a shared step
            """.trimIndent(),
        )
    }

    // ----- helpers -----------------------------------------------------------

    private fun gherkinFile(): GherkinFile = configuredFile as GherkinFile

    private fun steps(): List<GherkinStep> =
        PsiTreeUtil.findChildrenOfType(gherkinFile(), GherkinStep::class.java).toList()

    private fun sharedSteps(): List<GherkinStep> = steps().filter { it.text.contains("a shared step") }
    private fun uniqueStep(): GherkinStep = steps().first { it.text.contains("a unique step") }

    /** Native python-line breakpoints synced into the .py file. */
    private fun codeBps(): List<XBreakpoint<*>> =
        XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter { it.sourcePosition?.file == pyVFile }

    private fun codeBpsAt(line: Int): List<XBreakpoint<*>> = codeBps().filter { it.sourcePosition?.line == line }

    private fun bodyLine(marker: String): Int {
        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(pyVFile)!!
        return doc.getLineNumber(doc.charsSequence.toString().indexOf("\"$marker\""))
    }

    private fun toggleGherkin(step: GherkinStep) {
        val line = step.getDocumentLine() ?: error("step has no document line")
        WriteCommandAction.runWriteCommandAction(project) { step.toggleGherkinBreakpoint(line) }
    }

    private fun toggleCodeBreakpoint(line: Int) {
        WriteCommandAction.runWriteCommandAction(project) {
            XDebuggerUtil.getInstance().toggleLineBreakpoint(project, pyVFile, line)
        }
    }

    private fun removeBreakpoint(bp: XBreakpoint<*>) {
        WriteCommandAction.runWriteCommandAction(project) { XDebuggerUtil.getInstance().removeBreakpoint(project, bp) }
    }

    private fun setEnabled(bp: XBreakpoint<*>, enabled: Boolean) {
        WriteCommandAction.runWriteCommandAction(project) { bp.isEnabled = enabled }
    }

    private fun codeBp(): XBreakpoint<*>? = codeBpsAt(bodyLine("shared")).firstOrNull()

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
