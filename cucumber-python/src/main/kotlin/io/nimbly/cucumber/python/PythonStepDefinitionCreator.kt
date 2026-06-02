/*
 * Cucumber for Python
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

/**
 * Backs the "Create step definition" quick-fix for behave: creates a `.py`
 * file under `features/steps/` and inserts a `@given/@when/@then`-decorated
 * `step_impl` function for the missing step — mirroring what the Cucumber.js
 * creator does for JavaScript.
 */
class PythonStepDefinitionCreator : AbstractStepDefinitionCreator() {

    private val keywordProvider = JsonGherkinKeywordProvider.getKeywordProvider(true)

    override fun createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile {
        val fileName = if (name.endsWith(".py")) name else "$name.py"
        directory.findFile(fileName)?.let { return it }
        return WriteAction.compute<PsiFile, RuntimeException> {
            val file = directory.createFile(fileName)
            val doc = PsiDocumentManager.getInstance(directory.project).getDocument(file)
            if (doc != null) {
                doc.setText("from behave import *\n")
                PsiDocumentManager.getInstance(directory.project).commitDocument(doc)
            }
            file
        }
    }

    override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {
        if (file !is PyFile) return false
        val project = file.project
        val decorator = behaveDecoratorFor(step)
        val keywordText = step.keyword.text.trim()
        // Parameterize numbers / quoted strings / outline <columns> into behave
        // "parse" placeholders so the step matches variable values, not literals.
        val (parsePattern, args) = parameterize(step.name)
        val patternEsc = escapePyString(parsePattern)
        val rawEsc = escapePyString(step.name)
        val signature = (listOf("context") + args).joinToString(", ")

        WriteCommandAction.runWriteCommandAction(project) {
            val psiDocMgr = PsiDocumentManager.getInstance(project)
            val document = psiDocMgr.getDocument(file) ?: return@runWriteCommandAction
            val sb = StringBuilder(document.text)

            if (!HAS_BEHAVE_IMPORT.containsMatchIn(document.text)) {
                sb.insert(0, "from behave import *\n\n")
            }
            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
            sb.append("\n@").append(decorator).append("(u'").append(patternEsc).append("')\n")
            sb.append("def step_impl(").append(signature).append("):\n")
            sb.append("    raise NotImplementedError(u'STEP: ")
                .append(keywordText).append(" ").append(rawEsc).append("')\n")

            document.setText(sb.toString())
            psiDocMgr.commitDocument(document)

            // Navigate to the freshly inserted function.
            val newFn = (psiDocMgr.getPsiFile(document) as? PyFile)?.topLevelFunctions?.lastOrNull()
            if (newFn != null) {
                OpenFileDescriptor(project, file.virtualFile, newFn.textOffset)
                    .navigate(true)
            }
        }
        return false
    }

    override fun getDefaultStepFileName(step: GherkinStep): String = "steps"

    /** behave convention: step defs live in `features/steps/`. */
    override fun getDefaultStepDefinitionFolderPath(step: GherkinStep): String {
        val featureDir = step.containingFile.containingDirectory?.virtualFile
        val stepsChild = featureDir?.findChild("steps")
        if (stepsChild != null) return stepsChild.path
        return if (featureDir != null) "${featureDir.path}/steps"
        else super.getDefaultStepDefinitionFolderPath(step)
    }

    override fun getStepDefinitionFilePath(file: PsiFile): String =
        file.virtualFile?.name ?: file.name

    override fun validateNewStepDefinitionFileName(project: Project, name: String): Boolean =
        name.isNotBlank()

    // -- helpers -------------------------------------------------------------

    /** Resolve the behave decorator (`given`/`when`/`then`/`step`) for a step,
     *  honouring the Gherkin dialect and resolving And / But / asterisk to the
     *  controlling keyword by walking back through previous steps. */
    private fun behaveDecoratorFor(step: GherkinStep): String =
        when (resolveBaseKeyword(step).lowercase()) {
            "given" -> "given"
            "when" -> "when"
            "then" -> "then"
            else -> "step"
        }

    private fun resolveBaseKeyword(step: GherkinStep): String {
        val language = (step.containingFile as? GherkinFile)?.localeLanguage ?: "en"
        fun base(s: GherkinStep): String =
            keywordProvider.getBaseKeyword(language, s.keyword.text.trim()) ?: s.keyword.text.trim()

        val own = base(step)
        if (own !in FORBIDDEN) return own
        var prev = step.prevSibling
        while (prev != null) {
            if (prev is GherkinStep) {
                val pb = base(prev)
                if (pb !in FORBIDDEN) return pb
            }
            prev = prev.prevSibling
        }
        return "Given"
    }

    private fun escapePyString(text: String): String =
        text.replace("\\", "\\\\").replace("'", "\\'")

    /**
     * Turn a concrete step text into a behave "parse" pattern, replacing
     * variable parts with placeholders and collecting the function argument
     * names (in order):
     *  - `<column>` (scenario-outline) -> `{column}`  (name kept)
     *  - `"quoted"` / `'quoted'`        -> `"{argN}"`  (quotes preserved)
     *  - `12.5`                         -> `{argN:f}`
     *  - `414`                          -> `{argN:d}`
     */
    private fun parameterize(text: String): Pair<String, List<String>> {
        val args = mutableListOf<String>()
        var counter = 0
        fun next(): String { counter++; return "arg$counter" }
        val out = TOKEN.replace(text) { m ->
            val tok = m.value
            when {
                tok.startsWith("<") -> {
                    val name = sanitizeIdentifier(m.groupValues[1])
                    args += name
                    "{$name}"
                }
                tok.startsWith("\"") || tok.startsWith("'") -> {
                    val quote = tok.first()
                    val name = next(); args += name
                    "$quote{$name}$quote"
                }
                tok.contains('.') -> { val name = next(); args += name; "{$name:f}" }
                else -> { val name = next(); args += name; "{$name:d}" }
            }
        }
        return out to args
    }

    private fun sanitizeIdentifier(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (cleaned.isEmpty() || cleaned.first().isDigit()) "arg_$cleaned" else cleaned
    }

    companion object {
        private val FORBIDDEN = setOf("And", "But", "*")
        private val HAS_BEHAVE_IMPORT = Regex("""(?m)^\s*from\s+behave\s+import""")
        // Order matters: quoted strings & floats before bare integers.
        private val TOKEN = Regex("\"[^\"]*\"|'[^']*'|<([^>]+)>|\\d+\\.\\d+|\\d+")
    }
}
