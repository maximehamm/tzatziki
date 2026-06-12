package io.nimbly.tzatziki.rename

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.testFramework.ExtensionTestUtil
import io.nimbly.tzatziki.AbstractJavaTestCase
import io.nimbly.tzatziki.JavaTzatzikiExtensionPoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * End-to-end tests for the synchronised step rename (#8), Java side: create a `.feature` + the
 * associated Java step definitions, trigger [StepRename.apply], then verify BOTH the Gherkin steps
 * AND the step-definition pattern were rewritten correctly — across the parameter shapes:
 * no param, one param, several params, string param, data table, doc-string (multi-line) and
 * Scenario-Outline `<placeholder>`.
 */
class StepRenameEndToEndJavaTests : AbstractJavaTestCase() {

    private lateinit var javaFile: PsiFile

    override fun setUp() {
        super.setUp()
        // The fixture doesn't load our plugin.xml. Put our processor FIRST in the rename EP so
        // myFixture.renameElement(step, ...) routes to TzGherkinStepRenameProcessor (cucumber's
        // GherkinStepRenameProcessor is order="first" too, so a plain registerExtension won't win).
        val ep = RenamePsiElementProcessor.EP_NAME
        ExtensionTestUtil.maskExtensions(ep, listOf(TzGherkinStepRenameProcessor()) + ep.extensionList, testRootDisposable)
    }

    // ---- cases --------------------------------------------------------------

    fun testNoParameter_renamesPatternAndAllSiblings() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("a shared step") public void shared() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given a shared step
              Scenario: B
                Given a shared step
            """,
        )
        rename("a shared step", "a common step")

        assertEquals("a common step", pattern("shared"))
        assertEquals(2, occurrences("Given a common step"))
        assertEquals(0, occurrences("a shared step"))
    }

    fun testOneParameter_keepsValueRenamesLiteral() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} cukes") public void count() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I have 5 cukes
            """,
        )
        rename("I have 5 cukes", "I have 5 cucumbers")

        assertEquals("I have {int} cucumbers", pattern("count"))
        assertTrue(feature().contains("Given I have 5 cucumbers"))
    }

    fun testSeveralParameters() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} red {word}") public void multi() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I have 5 red apples
            """,
        )
        rename("I have 5 red apples", "I have 5 green apples")

        assertEquals("I have {int} green {word}", pattern("multi"))
        assertTrue(feature().contains("Given I have 5 green apples"))
    }

    fun testStringParameter() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I say {string}") public void say() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I say "hello"
            """,
        )
        rename("""I say "hello"""", """I shout "hello"""")

        assertEquals("I shout {string}", pattern("say"))
        assertTrue(feature().contains("""Given I shout "hello""""))
    }

    fun testDataTableParameter_tablePreserved() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("the following data") public void data(io.cucumber.datatable.DataTable t) {}
            }""",
            """
            Feature: F
              Scenario: A
                Given the following data
                  | name | age |
                  | Joe  | 42  |
            """,
        )
        rename("the following data", "the following records")

        assertEquals("the following records", pattern("data"))
        assertTrue(feature().contains("Given the following records"))
        // The attached data table must be left intact.
        assertTrue(feature().contains("| name | age |"))
        assertTrue(feature().contains("| Joe  | 42  |"))
    }

    fun testDocStringMultiLine_docStringPreserved() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("the doc") public void doc(String s) {}
            }""",
            "Feature: F\n" +
            "  Scenario: A\n" +
            "    Given the doc\n" +
            "      \"\"\"\n" +
            "      line one\n" +
            "      line two\n" +
            "      \"\"\"\n",
        )
        rename("the doc", "the document")

        assertEquals("the document", pattern("doc"))
        assertTrue(feature().contains("Given the document"))
        // The attached doc-string must be left intact.
        assertTrue(feature().contains("line one"))
        assertTrue(feature().contains("line two"))
    }

    fun testScenarioOutlinePlaceholder_preservesAngleBrackets() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} cukes") public void count() {}
            }""",
            """
            Feature: F
              Scenario: literal
                Given I have 5 cukes
              Scenario Outline: outlined
                Given I have <count> cukes
                Examples:
                  | count |
                  | 7     |
            """,
        )
        rename("I have 5 cukes", "I have 5 cucumbers")

        assertEquals("I have {int} cucumbers", pattern("count"))
        assertTrue(feature().contains("Given I have 5 cucumbers"))
        // The Scenario-Outline sibling is renamed too, keeping its <placeholder>.
        assertTrue(feature().contains("Given I have <count> cucumbers"))
    }

    // ---- adversarial / strict cases ----------------------------------------

    fun testSiblingsKeepTheirOwnDifferentValues() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} cukes") public void count() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I have 5 cukes
              Scenario: B
                Given I have 99 cukes
            """,
        )
        rename("I have 5 cukes", "I have 5 cucumbers")

        assertEquals("I have {int} cucumbers", pattern("count"))
        assertTrue(feature().contains("Given I have 5 cucumbers"))
        assertTrue(feature().contains("Given I have 99 cucumbers"))   // sibling keeps its OWN value
        assertFalse("old literal must be gone", feature().contains("cukes"))
    }

    fun testRenameInitiatedFromOutlineStep() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} cukes") public void count() {}
            }""",
            """
            Feature: F
              Scenario Outline: O
                Given I have <count> cukes
                Examples:
                  | count |
                  | 7     |
            """,
        )
        rename("I have <count> cukes", "I have <count> cucumbers")

        assertEquals("I have {int} cucumbers", pattern("count"))
        assertTrue(feature().contains("Given I have <count> cucumbers"))
        assertFalse(feature().contains("cukes"))
    }

    fun testChangingAValueIsNotARename() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} cukes") public void count() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I have 5 cukes
            """,
        )
        val before = feature()
        WriteCommandAction.runWriteCommandAction(project) {
            val step = steps().first { it.text.contains("I have 5 cukes") }
            assertNull("changing a value is not a literal rename", StepRename.apply(step, "I have 6 cukes"))
        }
        assertEquals("pattern must be untouched", "I have {int} cukes", pattern("count"))
        assertEquals("feature must be untouched", before, feature())
    }

    fun testChangingValueToLongerNumberIsNotARename() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} cukes") public void count() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I have 5 cukes
            """,
        )
        val before = feature()
        WriteCommandAction.runWriteCommandAction(project) {
            val step = steps().first { it.text.contains("I have 5 cukes") }
            // 5 -> 50 is a VALUE change (the step still matches `I have {int} cukes`), not a literal
            // rename. The naive engine anchors on the first "5" inside "50" and corrupts the pattern.
            assertNull("changing 5->50 is a value change, not a rename", StepRename.apply(step, "I have 50 cukes"))
        }
        assertEquals("pattern must be untouched", "I have {int} cukes", pattern("count"))
        assertEquals("feature must be untouched", before, feature())
    }

    fun testParameterizedStepWithDataTable_paramAndTablePreserved() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("the {int} following rows") public void rows(int n, io.cucumber.datatable.DataTable t) {}
            }""",
            """
            Feature: F
              Scenario: A
                Given the 2 following rows
                  | name | age |
                  | Joe  | 42  |
            """,
        )
        rename("the 2 following rows", "the 2 following records")

        assertEquals("the {int} following records", pattern("rows"))
        assertTrue(feature().contains("Given the 2 following records"))
        assertFalse("old literal gone", feature().contains("following rows"))
        // The data table (a separate node, NOT part of the step name) must be byte-for-byte intact.
        assertTrue(feature().contains("| name | age |"))
        assertTrue(feature().contains("| Joe  | 42  |"))
        assertEquals("table pipes intact", 6, feature().count { it == '|' })
    }

    fun testStepWithTrailingColonCommentAndDataTable() {
        // Mirrors France.feature line 50: a literal step ending with ':' , a '# comment' line, and
        // a data table underneath.
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("the following users and scores:") public void scores(io.cucumber.datatable.DataTable t) {}
            }""",
            "Feature: F\n" +
            "  Scenario: A\n" +
            "    Given the following users and scores:\n" +
            "      # @header: row\n" +
            "      | Name  | Score |\n" +
            "      | Alice | 92    |\n",
        )
        rename("the following users and scores:", "the following people and scores:")

        assertEquals("the following people and scores:", pattern("scores"))
        assertTrue(feature().contains("Given the following people and scores:"))
        assertFalse(feature().contains("users and scores"))
        // comment + table must survive
        assertTrue(feature().contains("# @header: row"))
        assertTrue(feature().contains("| Alice | 92    |"))
    }

    fun testOldPatternLiteralFullyRemovedFromDefinition() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("I have {int} red {word}") public void multi() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given I have 5 red apples
            """,
        )
        rename("I have 5 red apples", "I have 5 green apples")

        assertEquals("I have {int} green {word}", pattern("multi"))
        assertFalse("old literal 'red' must be gone from the step", feature().contains(" red "))
    }

    // ---- undo ---------------------------------------------------------------

    /** A single Ctrl-Z must roll back the WHOLE rename (definition + every Gherkin step), exactly as
     *  the editor handler runs it: ONE WriteCommandAction around [StepRename.applyAffected] — NOT the
     *  platform RenameProcessor used by [rename]. */
    fun testSingleUndoRevertsDefinitionAndAllSteps() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("a shared step") public void shared() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given a shared step
              Scenario: B
                Given a shared step
            """,
        )
        val featureBefore = feature()
        val javaBefore = javaFile.text

        val step = steps().first { it.text.contains("a shared step") }
        val affected = StepRename.affected(step)!!
        WriteCommandAction.runWriteCommandAction(project, "Rename Cucumber Step", null, Runnable {
            StepRename.applyAffected(affected, "a common step")
        })
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("a common step", pattern("shared"))
        assertTrue(feature().contains("a common step"))

        // In real usage the definition file is usually CLOSED — make sure a global undo from the
        // feature editor still reverts it.
        val fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        fem.closeFile(javaFile.virtualFile)
        val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
        val fileEditor = fem.getSelectedEditor(configuredFile!!.virtualFile)
        assertTrue("undo must be available", undo.isUndoAvailable(fileEditor))
        undo.undo(fileEditor)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertEquals("definition reverted by ONE undo", javaBefore, javaFile.text)
        assertEquals("feature reverted by ONE undo", featureBefore, feature())
    }

    /** Proactive flow: the user types over a step in place (their OWN command), then confirms the
     *  inlay → restore-to-original (command) + rename (command). A SINGLE undo of the rename must
     *  bring the definition AND every step back to the original — not leave the step renamed with the
     *  definition reverted. */
    fun testProactiveFlow_singleUndoRevertsToOriginal() {
        setup(
            """
            package io.nimbly;
            public class Steps {
                @io.cucumber.java.en.Given("a shared step") public void shared() {}
            }""",
            """
            Feature: F
              Scenario: A
                Given a shared step
              Scenario: B
                Given a shared step
            """,
        )
        val featureBefore = feature()
        val javaBefore = javaFile.text
        val pdm = com.intellij.psi.PsiDocumentManager.getInstance(project)
        val doc = pdm.getDocument(configuredFile!!)!!

        // 1) The user types over the first step (a separate "Typing" command).
        val firstStep = steps().first { it.text.contains("a shared step") }
        val nameStart = firstStep.textRange.startOffset + firstStep.text.indexOf("a shared step")
        WriteCommandAction.runWriteCommandAction(project, "Typing", null, Runnable {
            doc.replaceString(nameStart, nameStart + "a shared step".length, "a renamed step")
            pdm.commitDocument(doc)
        })
        val stepPtr = com.intellij.psi.SmartPointerManager.getInstance(project)
            .createSmartPsiElementPointer(steps().first { it.text.contains("a renamed step") })

        // 2) restore-to-original (command Q) then rename (command R), as the suggestion does.
        WriteCommandAction.runWriteCommandAction(project, "Rename Cucumber Step", null, Runnable {
            StepRename.restoreStepName(stepPtr.element!!, "a shared step")
        })
        WriteCommandAction.runWriteCommandAction(project, "Rename Cucumber Step", null, Runnable {
            StepRename.apply(stepPtr.element!!, "a renamed step")
        })
        pdm.commitAllDocuments()
        assertEquals("a renamed step", pattern("shared"))
        assertEquals(2, occurrences("Given a renamed step"))

        // 3) ONE undo must revert the definition AND all steps to the original.
        val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
        val fe = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            .getSelectedEditor(configuredFile!!.virtualFile)
        undo.undo(fe)
        pdm.commitAllDocuments()
        assertEquals("definition reverted by ONE undo", javaBefore, javaFile.text)
        assertEquals("every step reverted by ONE undo", featureBefore, feature())
    }

    // ---- helpers ------------------------------------------------------------

    private fun setup(java: String, gherkin: String) {
        configure(java)
        javaFile = configuredFile!!
        feature(gherkin)
    }

    /**
     * Triggers the rename through the REAL IDE entry point — the platform/cucumber rename
     * refactoring (`GherkinStepRenameProcessor` → `GherkinStepImpl.setName`) — NOT our (un-wired)
     * `StepRename.apply`. This is what actually runs when the user renames a step in the editor,
     * so the tests now reflect (and expose) the real behaviour, including the table/doc-string loss.
     */
    private fun rename(stepContains: String, newName: String) {
        val step = steps().first { it.text.contains(stepContains) }
        myFixture.renameElement(step, newName)
    }

    private fun steps(): List<GherkinStep> =
        PsiTreeUtil.findChildrenOfType(configuredFile, GherkinStep::class.java).toList()

    private fun feature(): String = configuredFile!!.text

    private fun occurrences(s: String): Int =
        Regex(Regex.escape(s)).findAll(feature()).count()

    private fun pattern(methodName: String): String {
        val method = PsiTreeUtil.findChildrenOfType(javaFile, PsiMethod::class.java).first { it.name == methodName }
        return JavaTzatzikiExtensionPoint().getStepPattern(method)!!.raw
    }
}
