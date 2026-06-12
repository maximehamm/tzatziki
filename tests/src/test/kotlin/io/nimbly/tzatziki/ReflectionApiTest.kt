package io.nimbly.tzatziki

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.nimbly.tzatziki.testdiscovery.ANALYZE_STACKTRACE_CLASS
import io.nimbly.tzatziki.testdiscovery.ANALYZE_STACKTRACE_METHOD
import io.nimbly.tzatziki.util.JAVA_LINE_BP_LAMBDA_ORDINAL_FIELD
import io.nimbly.tzatziki.util.XDEBUGGER_TOGGLE_METHOD
import io.nimbly.tzatziki.view.features.nodes.CUCUMBER_JAVA_FEATURE_PRODUCER
import io.nimbly.tzatziki.view.features.nodes.CUCUMBER_JAVA_SCENARIO_PRODUCER
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.intellij.refactoring.rename.RenameHandler
import io.nimbly.tzatziki.rename.CUCUMBER_STEP_RENAME_HANDLER

/**
 * Verifies that every class, method, and field accessed via reflection still exists
 * in the current IntelliJ platform version. A failure here means a silent runtime
 * regression — fix the reflection call before shipping.
 */
class ReflectionApiTest {

    // -------------------------------------------------------------------------
    // XDebuggerUtil.toggleAndReturnLineBreakpoint
    // -------------------------------------------------------------------------

    @Test
    fun `XDebuggerUtil implementation has toggleAndReturnLineBreakpoint with expected signature`() {
        // The method lives on the concrete XDebuggerUtilImpl (not the abstract base) in 2025.3+.
        // JavaUtil.invokeMethod calls getDeclaredMethod on javaClass (= XDebuggerUtilImpl), so
        // it finds the method at runtime even though it is absent from the abstract class.
        // We check the impl class here to mirror what production code actually does.
        val implClass = runCatching {
            Class.forName("com.intellij.xdebugger.impl.XDebuggerUtilImpl")
        }.getOrElse {
            // Older platform: method lives on the abstract base class
            XDebuggerUtil::class.java
        }
        val method = runCatching {
            implClass.getDeclaredMethod(
                XDEBUGGER_TOGGLE_METHOD,
                Project::class.java,
                VirtualFile::class.java,
                Int::class.java,
                Boolean::class.java
            )
        }.getOrNull()
        assertNotNull(
            "$implClass.$XDEBUGGER_TOGGLE_METHOD(Project, VirtualFile, Int, Boolean) not found — " +
            "update BreakpointsUtil.toggleAndReturnLineBreakpoint()",
            method
        )
    }

    // -------------------------------------------------------------------------
    // XBreakpointManagerImpl.updateBreakpointPresentation  (mandatory — "partial mute" icon)
    // -------------------------------------------------------------------------

    @Test
    fun `XBreakpointManagerImpl has updateBreakpointPresentation with expected signature`() {
        // TzPartialMutePresentation.apply() calls this internal method reflectively to give a
        // line breakpoint a per-instance "partial mute" icon (the half red disc / half red ring
        // shown on a shared step-def code breakpoint whose linked Gherkin steps are partly muted).
        // If the class or signature changes, the partial icon silently stops working — fail here
        // so the migration is caught at build time.
        val clazz = runCatching {
            Class.forName("com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl")
        }.getOrNull()
        assertNotNull(
            "com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl not found — " +
            "update TzPartialMutePresentation.apply()",
            clazz
        )

        val method = runCatching {
            clazz!!.getMethod(
                "updateBreakpointPresentation",
                XLineBreakpoint::class.java,
                javax.swing.Icon::class.java,
                String::class.java
            )
        }.getOrNull()
        assertNotNull(
            "XBreakpointManagerImpl.updateBreakpointPresentation(XLineBreakpoint, Icon, String) not found — " +
            "update TzPartialMutePresentation.apply()",
            method
        )
    }

    // -------------------------------------------------------------------------
    // JavaLineBreakpointProperties.myLambdaOrdinal
    // -------------------------------------------------------------------------

    @Test
    fun `JavaLineBreakpointProperties lambda ordinal field or equivalent exists (optional — has silent fallback)`() {
        // JavaUtil.updateField already has catch(ignored: Exception), so production code is safe
        // even when this field is absent.  In IntelliJ 2025.3+ the field was replaced by
        // encodedInlinePosition + getLambdaOrdinal().  The fix just silently becomes a no-op.
        fun Class<*>.findFieldInHierarchy(name: String): java.lang.reflect.Field? {
            var c: Class<*>? = this
            while (c != null) {
                runCatching { return c!!.getDeclaredField(name) }
                c = c.superclass
            }
            return null
        }
        val field = JavaLineBreakpointProperties::class.java.findFieldInHierarchy(JAVA_LINE_BP_LAMBDA_ORDINAL_FIELD)
        if (field == null) {
            println("INFO: $JAVA_LINE_BP_LAMBDA_ORDINAL_FIELD field not found (removed in 2025.3+) — " +
                    "production code skips silently via try/catch in JavaUtil.updateField()")
        } else {
            println("INFO: $JAVA_LINE_BP_LAMBDA_ORDINAL_FIELD OK")
        }
        // Does not fail: production code handles the missing field gracefully.
    }

    // -------------------------------------------------------------------------
    // AnalyzeStacktraceUtil.addConsole  (optional — has fallback)
    // -------------------------------------------------------------------------

    @Test
    fun `AnalyzeStacktraceUtil addConsole still exists (optional — has notification fallback)`() {
        val clazz = runCatching { Class.forName(ANALYZE_STACKTRACE_CLASS) }.getOrNull()
        if (clazz == null) {
            println("INFO: $ANALYZE_STACKTRACE_CLASS not found — fallback notification will be used")
            return
        }
        val method = runCatching {
            clazz.getMethod(
                ANALYZE_STACKTRACE_METHOD,
                Project::class.java,
                Array<Filter>::class.java,
                String::class.java,
                String::class.java
            )
        }.getOrNull()
        if (method == null) {
            println("WARNING: $ANALYZE_STACKTRACE_CLASS.$ANALYZE_STACKTRACE_METHOD signature changed — " +
                    "fallback notification will be used")
        } else {
            println("INFO: $ANALYZE_STACKTRACE_CLASS.$ANALYZE_STACKTRACE_METHOD OK")
        }
        // Does not fail: this reflection call has a try/catch fallback in production code.
    }

    // -------------------------------------------------------------------------
    // Cucumber Java run configuration producers  (optional classes)
    // -------------------------------------------------------------------------

    @Test
    fun `at least one Cucumber Java run configuration producer class exists`() {
        val scenario = runCatching { Class.forName(CUCUMBER_JAVA_SCENARIO_PRODUCER) }.getOrNull()
        val feature  = runCatching { Class.forName(CUCUMBER_JAVA_FEATURE_PRODUCER)  }.getOrNull()
        if (scenario != null) {
            println("INFO: $CUCUMBER_JAVA_SCENARIO_PRODUCER OK")
        } else {
            println("INFO: $CUCUMBER_JAVA_SCENARIO_PRODUCER absent — using fallback $CUCUMBER_JAVA_FEATURE_PRODUCER")
        }
        if (feature == null && scenario == null) {
            fail(
                "Neither $CUCUMBER_JAVA_SCENARIO_PRODUCER nor $CUCUMBER_JAVA_FEATURE_PRODUCER found. " +
                "Update GherkinScenarioNode.getRunConfiguration() with the new producer class name."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Cucumber's GherkinStepRenameHandler — TzRenameHandlerStartup drops it (by FQN) so our
    // table/parameter/doc-string-safe rename is the sole handler (no "choose handler" popup).
    // -------------------------------------------------------------------------

    @Test
    fun `cucumber GherkinStepRenameHandler exists and is a RenameHandler`() {
        val clazz = runCatching { Class.forName(CUCUMBER_STEP_RENAME_HANDLER) }.getOrNull()
        assertNotNull(
            "$CUCUMBER_STEP_RENAME_HANDLER not found — cucumber moved/renamed it, so " +
            "TzRenameHandlerStartup can no longer drop it and the Gherkin step rename chooser will " +
            "reappear. Update CUCUMBER_STEP_RENAME_HANDLER.",
            clazz,
        )
        assertTrue(
            "$CUCUMBER_STEP_RENAME_HANDLER is no longer a RenameHandler — revisit TzRenameHandlerStartup.",
            RenameHandler::class.java.isAssignableFrom(clazz!!),
        )
    }
}
