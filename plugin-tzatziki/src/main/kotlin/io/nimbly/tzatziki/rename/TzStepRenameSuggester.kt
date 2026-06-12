/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.util.concurrent.Callable
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.KeyEvent

/** Delay (ms) after a caret move / keystroke before re-evaluating the suggestion. */
private const val DELAY_MS = 300
private const val LABEL = "Rename steps and references…"

/**
 * Proactive in-editor suggestion for feature #8: when the user starts ALTERING a Gherkin step that
 * resolves to a step definition, a clickable inlay — "Rename step and references…" — appears below
 * the line; clicking it opens the Cucumber+ rename dialog (which propagates to the definition + all
 * sibling steps, preserving parameters / tables / doc-strings).
 *
 * Why a pre-edit snapshot: once the step text is altered it no longer resolves to its definition, so
 * we capture {definition, pattern, original name} when the caret ENTERS the step (cheap, cached) and
 * drive the rename from that snapshot. Per-keystroke cost is a single line-text comparison; the
 * project-wide sibling scan is deferred to the click.
 */
class TzStepRenameSuggester(private val editor: Editor, private val project: Project) : Disposable {

    private class Snapshot(
        val line: Int,
        val originalLineText: String,
        /** Column (char index in the line) where the step NAME starts — i.e. right after the keyword.
         *  Used to align the inlay's text under the start of the edited step. */
        val nameStartColumn: Int,
        val stepPtr: SmartPsiElementPointer<GherkinStep>,
        val defPtr: SmartPsiElementPointer<PsiElement>,
        val pattern: StepPatternInfo,
        val originalName: String,
    )

    private var snapshot: Snapshot? = null
    private var inlay: Inlay<*>? = null
    /** Set when the user dismisses the inlay with Escape; reset when the caret enters a new step. */
    private var dismissed = false
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    /** Escape dismisses the inlay (for the current step) without leaving the line. Registered on the
     *  IdeEventQueue so it sees the FIRST Escape press (a raw KeyListener on the editor only gets it
     *  after the IDE's key dispatcher, which can swallow the first press → needing two). We do NOT
     *  consume the event, so a competing Escape consumer (e.g. a completion popup) still reacts. */
    private val escDispatcher = IdeEventQueue.EventDispatcher { e ->
        if (e is KeyEvent && e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_ESCAPE &&
            inlay != null && editor.contentComponent.isShowing && editor.contentComponent.hasFocus()
        ) {
            dismissed = true
            removeInlay()
        }
        false
    }

    init {
        IdeEventQueue.getInstance().addDispatcher(escDispatcher, this)
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = schedule(reSnapshot = true)
        }, this)
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = schedule(reSnapshot = false)
        }, this)
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseClicked(event: EditorMouseEvent) {
                val current = inlay ?: return
                if (editor.inlayModel.getElementAt(event.mouseEvent.point) === current) {
                    event.consume()
                    triggerRename()
                }
            }
        }, this)
        editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(event: EditorMouseEvent) {
                val over = inlay != null && editor.inlayModel.getElementAt(event.mouseEvent.point) === inlay
                (editor as? EditorEx)?.setCustomCursor(
                    this@TzStepRenameSuggester,
                    if (over) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else null,
                )
            }
        }, this)
    }

    private fun schedule(reSnapshot: Boolean) {
        alarm.cancelAllRequests()
        // The line-text comparison (updateInlay) is free → EDT. Resolving a step to its definition
        // hits the stub index (a SLOW operation, forbidden on EDT) → do it in a background read action.
        alarm.addRequest({ runCatching { if (reSnapshot) refreshSnapshotAsync() else updateInlay() } }, DELAY_MS)
    }

    private fun caretLine(): Int = editor.caretModel.logicalPosition.line

    /** Capture (or clear) the pre-edit snapshot when the caret enters a different step line. The
     *  step → definition resolution runs OFF the EDT; the snapshot is then adopted on the EDT. */
    private fun refreshSnapshotAsync() {
        val line = caretLine()
        if (snapshot?.line == line) { updateInlay(); return }   // same line → keep snapshot while typing
        snapshot = null
        dismissed = false                                        // new step → the Escape dismissal is cleared
        removeInlay()
        ReadAction.nonBlocking(Callable { computeSnapshot(line) })
            .expireWith(this)
            .coalesceBy(this)
            .inSmartMode(project)   // resolveToDefinitions touches the stub index → wait out dumb mode
            .finishOnUiThread(ModalityState.any()) { snap ->
                if (snap != null && !editor.isDisposed && caretLine() == snap.line) {
                    snapshot = snap
                    runCatching { updateInlay() }
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Runs inside a background read action. */
    private fun computeSnapshot(line: Int): Snapshot? {
        val step = stepAtLine(line) ?: return null
        val original = step.name?.takeIf { it.isNotBlank() } ?: return null
        val (def, pattern) = StepRename.renamableDef(step) ?: return null
        val spm = SmartPointerManager.getInstance(project)
        val lineStart = editor.document.getLineStartOffset(line)
        val rel = step.text.indexOf(original)        // keyword precedes; first occurrence is the name
        val nameStartColumn =
            if (rel >= 0) (step.textRange.startOffset + rel - lineStart).coerceAtLeast(0)
            else lineText(line).takeWhile { it == ' ' || it == '\t' }.length
        return Snapshot(line, lineText(line), nameStartColumn, spm.createSmartPsiElementPointer(step), spm.createSmartPsiElementPointer(def), pattern, original)
    }

    /** Show the inlay iff the snapshot step's line has been altered and the caret is still on it. */
    private fun updateInlay() {
        val s = snapshot ?: return removeInlay()
        if (dismissed || s.line >= editor.document.lineCount || caretLine() != s.line) return removeInlay()
        if (lineText(s.line) != s.originalLineText) showInlay(s) else removeInlay()
    }

    private fun showInlay(s: Snapshot) {
        if (inlay?.isValid == true) return
        val offset = editor.document.getLineStartOffset(s.line)
        inlay = editor.inlayModel.addBlockElement(offset, true, false, 0, RenameInlayRenderer(nameStartPx(s)))
    }

    /** Pixel x (in editor-font metrics) of the step NAME start — to align the inlay under it. */
    private fun nameStartPx(s: Snapshot): Int {
        val prefix = s.originalLineText.substring(0, s.nameStartColumn.coerceIn(0, s.originalLineText.length))
        return editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN)).stringWidth(prefix)
    }

    private fun removeInlay() {
        inlay?.let { Disposer.dispose(it) }
        inlay = null
    }

    private fun triggerRename() {
        val s = snapshot ?: return
        removeInlay()
        snapshot = null
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        // The sibling collection scans the project (stub index) → off the EDT; open the dialog back on it.
        ReadAction.nonBlocking(Callable {
            val def = s.defPtr.element ?: return@Callable null
            val step = s.stepPtr.element ?: return@Callable null
            StepRename.affectedFrom(def, s.pattern, step, s.originalName)
        })
            .expireWith(this)
            .inSmartMode(project)   // the project-wide sibling scan touches indexes → wait out dumb mode
            .finishOnUiThread(ModalityState.nonModal()) { affected ->
                if (affected != null && !editor.isDisposed)
                    promptStepRenameRestoringFirst(project, affected, s.stepPtr, s.originalName)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun stepAtLine(line: Int): GherkinStep? {
        val doc = editor.document
        if (line >= doc.lineCount) return null
        val psi = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return null
        val start = doc.getLineStartOffset(line)
        val end = doc.getLineEndOffset(line)
        var probe = start
        while (probe <= end && probe < doc.textLength) {
            val step = PsiTreeUtil.getParentOfType(psi.findElementAt(probe), GherkinStep::class.java, false)
            if (step != null) return step
            probe++
        }
        return null
    }

    private fun lineText(line: Int): String {
        val doc = editor.document
        if (line >= doc.lineCount) return ""
        return doc.getText(TextRange(doc.getLineStartOffset(line), doc.getLineEndOffset(line)))
    }

    override fun dispose() = removeInlay()   // the IdeEventQueue dispatcher is auto-removed (parent = this)

    /** Renders the clickable "Rename step and references…" hint as a link-styled block inlay. */
    private class RenameInlayRenderer(private val indentPx: Int) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val fm = inlay.editor.contentComponent.getFontMetrics(font(inlay.editor))
            return indentPx + fm.stringWidth(LABEL) + JBUI.scale(12)
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val editor = inlay.editor
            g.font = font(editor)
            val fm = g.fontMetrics
            g.color = LINK_COLOR
            val x = targetRegion.x + indentPx + JBUI.scale(4)
            val baseline = targetRegion.y + fm.ascent + ((targetRegion.height - fm.height).coerceAtLeast(0)) / 2
            g.drawString(LABEL, x, baseline)
            g.drawLine(x, baseline + JBUI.scale(1), x + fm.stringWidth(LABEL), baseline + JBUI.scale(1))
        }

        /** Editor font, rendered a bit smaller so the hint stays discreet below the step. */
        private fun font(editor: Editor): Font {
            val base = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            return base.deriveFont((base.size2D * 0.85f).coerceAtLeast(10f))
        }

        private companion object {
            private val LINK_COLOR = JBColor.namedColor("Link.activeForeground", JBColor(0x589DF6, 0x548AF7))
        }
    }
}

/**
 * Attaches a [TzStepRenameSuggester] to every Gherkin main editor (and disposes it on release).
 * Registered as `com.intellij.editorFactoryListener`.
 */
class TzStepRenameSuggesterFactory : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor.editorKind != EditorKind.MAIN_EDITOR) return
        val project = editor.project ?: return
        val vf = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (vf.fileType != GherkinFileType.INSTANCE) return
        editor.putUserData(KEY, TzStepRenameSuggester(editor, project))
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        event.editor.getUserData(KEY)?.let {
            Disposer.dispose(it)
            event.editor.putUserData(KEY, null)
        }
    }

    private companion object {
        private val KEY = Key.create<TzStepRenameSuggester>("tzatziki.step.rename.suggester")
    }
}
