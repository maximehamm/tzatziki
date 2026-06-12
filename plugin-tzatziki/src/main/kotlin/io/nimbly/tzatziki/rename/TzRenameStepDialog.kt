/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

import com.intellij.lang.Language
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.LanguageTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Rename dialog for a Gherkin step (#8): a Gherkin-highlighted text field for the new step text plus
 * a LIVE, syntax-highlighted preview of every reference that will be renamed — the step definition
 * (in its host language) and all bound Gherkin steps. OK is disabled when the new text isn't a valid
 * rename (see [StepRenameEngine]).
 */
class TzRenameStepDialog(
    private val project: Project,
    private val affected: StepRename.Affected,
) : DialogWrapper(project) {

    /** The name the definition currently matches (pre-edit) — what we rename FROM. */
    private val oldName: String = affected.originalName
    private val defLanguage: Language = affected.defElement.containingFile?.language ?: GherkinLanguage.INSTANCE

    /** Read-only keyword prefix (e.g. "Given ") shown inside the field so the Gherkin highlighter
     *  sees a full step; only the text AFTER it is editable. */
    private val prefix: String = affected.editedStep.keyword?.text?.trim().orEmpty().let { if (it.isEmpty()) "" else "$it " }

    /** Edit field with Gherkin syntax highlighting; the keyword [prefix] is guarded (non-editable). */
    private val field = LanguageTextField(GherkinLanguage.INSTANCE, project, prefix + affected.initialText, true).apply {
        addSettingsProvider { editor ->
            if (prefix.isNotEmpty()) {
                (editor.document as? DocumentEx)?.let { doc ->
                    doc.createGuardedBlock(0, prefix.length)
                    // Silently ignore edits to the guarded keyword prefix (no "Guarded Block" popup).
                    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(doc) { }
                }
                editor.caretModel.moveToOffset(prefix.length.coerceAtMost(editor.document.textLength))
                editor.selectionModel.removeSelection()
            }
        }
    }

    private val model = object : DefaultTableModel(arrayOf("Location", "Preview"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(model)

    init {
        title = "Cucumber+ Rename step"
        model.addRow(arrayOf(location(affected.defElement), defDisplay(affected.pattern.raw)))
        affected.steps.forEach { model.addRow(arrayOf(location(it), stepDisplay(it, it.name ?: ""))) }

        // "Location" column: a clickable hyperlink (cancels the dialog & navigates to the def/step),
        // pinned to the minimum width that fits its content + header.
        val loc = table.columnModel.getColumn(0)
        loc.cellRenderer = object : ColoredTableCellRenderer() {
            override fun customizeCellRenderer(t: JTable, value: Any?, sel: Boolean, focus: Boolean, row: Int, col: Int) {
                append(value?.toString() ?: "", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES)
            }
        }
        var w = table.getFontMetrics(table.font).stringWidth(model.getColumnName(0)) + JBUI.scale(20)
        for (row in 0 until table.rowCount) {
            val comp = table.prepareRenderer(table.getCellRenderer(row, 0), row, 0)
            w = maxOf(w, comp.preferredSize.width + JBUI.scale(10))
        }
        loc.minWidth = w; loc.maxWidth = w; loc.preferredWidth = w
        // "Preview" column: syntax-highlighted (Gherkin for steps, def language for the definition).
        table.columnModel.getColumn(1).cellRenderer = PreviewRenderer()

        // Click on a Location → close (cancel) and navigate to that definition / step.
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (table.columnAtPoint(e.point) != 0) return
                val element = elementForRow(table.rowAtPoint(e.point)) ?: return
                close(CANCEL_EXIT_CODE)
                PsiNavigateUtil.navigate(element)
            }
            override fun mouseMoved(e: MouseEvent) {}
        })
        table.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                table.cursor = if (table.columnAtPoint(e.point) == 0)
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        })

        field.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = refresh()
        })
        init()
        refresh()
    }

    fun newName(): String = field.text.let { if (it.length >= prefix.length) it.substring(prefix.length) else it }.trim()

    override fun getPreferredFocusedComponent(): JComponent = field

    override fun createCenterPanel(): JComponent {
        val fieldRow = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(JBLabel("Rename step to:"), BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
        }
        val header = JPanel(BorderLayout()).apply {
            add(fieldRow, BorderLayout.NORTH)
            add(JBLabel("References that will be renamed:").apply { border = JBUI.Borders.empty(8, 0, 4, 0) }, BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(780), JBUI.scale(300))
        }
    }

    private fun refresh() {
        val nn = newName()
        val result =
            if (nn.isNotEmpty() && nn != oldName)
                StepRenameEngine.rename(affected.pattern.raw, affected.pattern.kind, oldName, nn, affected.siblings.map { it.name })
            else null

        // Single "Preview" column: starts at the current value, updates live to the renamed value
        // (stays unchanged when the new text isn't a valid rename — OK is then disabled).
        model.setValueAt(defDisplay(result?.newPattern ?: affected.pattern.raw), 0, 1)
        affected.steps.forEachIndexed { i, step ->
            val newName = when {
                result == null -> step.name ?: ""
                step === affected.editedStep -> nn
                else -> affected.siblings.indexOf(step).let { idx ->
                    if (idx in result.newSiblings.indices) result.newSiblings[idx] else step.name ?: ""
                }
            }
            model.setValueAt(stepDisplay(step, newName), i + 1, 1)
        }
        setOKActionEnabled(result != null)
        setErrorText(
            if (result == null && nn.isNotEmpty() && nn != oldName)
                "Not a valid rename — keep the same parameters, in the same order. Changing a parameter value, or reordering / removing parameters, isn't supported."
            else null,
        )
    }

    /** The step-definition shown as its real source (the annotation/call), with [pattern]
     *  substituted, so the def-language highlighter renders e.g. `@Then("...")`. */
    private fun defDisplay(pattern: String): String {
        val text = affected.defElement.text ?: return pattern
        val raw = affected.pattern.raw
        return text.replace("\"$raw\"", "\"$pattern\"").replace("'$raw'", "'$pattern'")
    }

    /** A step shown with its keyword prefix (e.g. `Given ...`) so the Gherkin highlighter recognises it. */
    private fun stepDisplay(step: GherkinStep, name: String): String {
        val keyword = step.keyword?.text?.trim().orEmpty()
        return if (keyword.isEmpty()) name else "$keyword $name"
    }

    private fun location(element: PsiElement): String {
        val file = element.containingFile ?: return "?"
        val doc = PsiDocumentManager.getInstance(project).getDocument(file)
        val line = doc?.let { it.getLineNumber(element.textOffset) + 1 } ?: 0
        return "${file.name}:$line"
    }

    /** PSI element behind a table row: row 0 = the step definition, the rest = the Gherkin steps. */
    private fun elementForRow(row: Int): PsiElement? = when {
        row < 0 -> null
        row == 0 -> affected.defElement
        else -> affected.steps.getOrNull(row - 1)
    }

    /**
     * Renders a Preview cell with the language's BASE (lexer) syntax highlighting — Gherkin for the
     * step rows, the def language for row 0 — then overlays the tokens the lexer doesn't colour in a
     * fragment, all using the editor's REAL colour keys (nothing invented):
     *  - step parameters / numbers / quoted args → `GHERKIN_REGEXP_PARAMETER`,
     *  - Scenario-Outline `<placeholders>` → `GHERKIN_OUTLINE_PARAMETER_SUBSTITUTION`,
     *  - the def `{...}` parameters → `GHERKIN_REGEXP_PARAMETER`,
     *  - the def `@Annotation` (Java/Kotlin only) → `ANNOTATION_NAME` (fallback `METADATA`).
     */
    private inner class PreviewRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
            val text = value?.toString() ?: return
            if (text.isEmpty()) return
            val language = if (row == 0) defLanguage else GherkinLanguage.INSTANCE
            val scheme = EditorColorsManager.getInstance().globalScheme
            val perChar = arrayOfNulls<SimpleTextAttributes>(text.length)

            SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, null)?.let { hl ->
                val lexer = hl.highlightingLexer
                lexer.start(text)
                while (lexer.tokenType != null) {
                    val a = hl.getTokenHighlights(lexer.tokenType).lastOrNull()
                        ?.let { scheme.getAttributes(it) }?.let { SimpleTextAttributes.fromTextAttributes(it) }
                    if (a != null) for (i in lexer.tokenStart until minOf(lexer.tokenEnd, perChar.size)) perChar[i] = a
                    lexer.advance()
                }
            }

            val param = colorOf(scheme, PARAM_KEY)
            if (row == 0) {
                overlay(perChar, text, BRACE_PARAM, param)
                if (defLanguage.id in JVM_LANGUAGE_IDS) {
                    val anno = (scheme.getAttributes(ANNOTATION_KEY)?.takeIf { it.foregroundColor != null }
                        ?: scheme.getAttributes(DefaultLanguageHighlighterColors.METADATA))?.let { SimpleTextAttributes.fromTextAttributes(it) }
                    overlay(perChar, text, ANNOTATION, anno)
                }
            } else {
                // Delimiters (" ", < >) and numbers use the param colour; the placeholder IDENTIFIER
                // inside <...> uses the (distinct) outline-substitution colour, so e.g. in
                // `"<Prenom>"` the quotes/brackets and the name `Prenom` are visibly different.
                val outlineName = colorOf(scheme, OUTLINE_KEY) ?: colorOf(scheme, DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                overlay(perChar, text, NUMBER, param)
                overlay(perChar, text, QUOTED, param)
                overlay(perChar, text, OUTLINE, param)
                overlay(perChar, text, OUTLINE_NAME, outlineName)
            }

            var i = 0
            while (i < text.length) {
                val a = perChar[i]
                var j = i + 1
                while (j < text.length && perChar[j] === a) j++
                append(text.substring(i, j), a ?: SimpleTextAttributes.REGULAR_ATTRIBUTES)
                i = j
            }
        }

        private fun overlay(perChar: Array<SimpleTextAttributes?>, text: String, regex: Regex, a: SimpleTextAttributes?) {
            if (a == null) return
            for (m in regex.findAll(text)) for (idx in m.range) perChar[idx] = a
        }

        private fun colorOf(scheme: com.intellij.openapi.editor.colors.EditorColorsScheme, key: TextAttributesKey): SimpleTextAttributes? =
            scheme.getAttributes(key)?.takeIf { it.foregroundColor != null }?.let { SimpleTextAttributes.fromTextAttributes(it) }
    }

    private companion object {
        private val ANNOTATION = Regex("@[\\w.]+")
        private val OUTLINE = Regex("<[^>]*>")
        private val OUTLINE_NAME = Regex("(?<=<)[^<>]+(?=>)")   // the identifier inside <...>
        private val QUOTED = Regex("\"[^\"]*\"")
        private val NUMBER = Regex("""(?<!\w)\d+(?:[.,]\d+)?(?!\w)""")
        private val BRACE_PARAM = Regex("\\{[^}]*}")
        /** Java's annotation-name key; resolved by external name (no Java-module dependency). */
        private val ANNOTATION_KEY: TextAttributesKey = TextAttributesKey.find("ANNOTATION_NAME")
        /** REAL cucumber/Gherkin colour keys (from GherkinHighlighter), resolved by external name.
         *  REGEXP_PARAMETER → PARAMETER ; OUTLINE_PARAMETER_SUBSTITUTION → INSTANCE_FIELD. */
        private val PARAM_KEY: TextAttributesKey = TextAttributesKey.find("GHERKIN_REGEXP_PARAMETER")
        private val OUTLINE_KEY: TextAttributesKey = TextAttributesKey.find("GHERKIN_OUTLINE_PARAMETER_SUBSTITUTION")
        private val JVM_LANGUAGE_IDS = setOf("JAVA", "kotlin")
    }
}
