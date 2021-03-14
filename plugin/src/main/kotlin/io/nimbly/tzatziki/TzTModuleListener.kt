package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.TzTModuleListener.AbstractWriteActionHandler
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import java.awt.event.MouseEvent

var TZATZIKI_AUTO_FORMAT : Boolean = true
var TZATZIKI_SMART_COPY : Boolean = true

const val EDITOR_UNINDENT_SELECTION = "EditorUnindentSelection"

class TzTModuleListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        if (!handlerInitialized) {
            initTypedHandler()
            initMouseListener(project)
            handlerInitialized = true
        }
    }

    private fun initTypedHandler() {

        val actionManager = EditorActionManager.getInstance()

        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_DELETE))
        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_BACKSPACE))

        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_CUT))
        actionManager.replaceHandler(FormatterHandler(ACTION_EDITOR_PASTE))

        actionManager.replaceHandler(TabHandler(ACTION_EDITOR_TAB))
        actionManager.replaceHandler(TabHandler(EDITOR_UNINDENT_SELECTION))

        actionManager.replaceHandler(EnterHandler())

        actionManager.replaceHandler(CopyHandler())
    }

    private fun initMouseListener(project: Project) {

        val mouseAdapter = object : EditorMouseAdapter() {
            override fun mouseReleased(e: EditorMouseEvent) {

                if (!TZATZIKI_SMART_COPY)
                    return

                TmarSelectionModeManager.releaseSelectionSwitch()

                val me = e.mouseEvent
                if (me.button == MouseEvent.BUTTON1
                    && me.clickCount == 3) {
                    val editor = e.editor
                    val logicalPosition = editor.xyToLogicalPosition(e.mouseEvent.point)
                    val offset = editor.logicalPositionToOffset(logicalPosition)
                    val table = editor.findTable(offset)
                    if (table != null) {
                        manageDoubleClicTableSelection(table, editor, offset)
                    }
                }
            }

            override fun mousePressed(e: EditorMouseEvent) {

                if (!TZATZIKI_SMART_COPY)
                    return

                val editor = e.editor

                // Swith selection mode
                if (editor.selectionModel.hasSelection()) return

                //System.out.println("M");
                val logicalPosition = editor.xyToLogicalPosition(e.mouseEvent.point)
                val offset = editor.logicalPositionToOffset(logicalPosition)

                // swith mode if needed
                TmarSelectionModeManager.switchEditorSelectionModeIfNeeded(editor, offset)
                TmarSelectionModeManager.blockSelectionSwitch()

                //
                // TRICKY : avoid Intellij to manage double clic !
                // Because  e.getMouseEvent().consume() is not manage by default implementation !
                val me = e.mouseEvent
                if (me.button == MouseEvent.BUTTON1
                    && me.clickCount == 3
                ) {
                    val table = editor.findTable(offset)
                    if (table != null) {
                        e.consume()
                        e.mouseEvent.consume()
                        JavaUtil.updateField(e.mouseEvent, "popupTrigger", true)
                        JavaUtil.updateField(e.mouseEvent, "button", 0)
                    }
                }
            }
        }

        val caretAdapter: CaretListener = object : CaretAdapter() {
            override fun caretPositionChanged(e: CaretEvent) {

                if (!TZATZIKI_SMART_COPY)
                    return

                val editor = e.editor
                val offset = editor.logicalPositionToOffset(e.newPosition)
                TmarSelectionModeManager.switchEditorSelectionModeIfNeeded(editor, offset)
            }
        }

        val editorFactory = EditorFactory.getInstance()
        val multicaster = editorFactory.eventMulticaster
        multicaster.addEditorMouseListener(mouseAdapter, project)
        multicaster.addCaretListener(caretAdapter, project)
    }

    private class FormatterHandler(actionId : String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            doDefault(editor, caret, dataContext)
            if (TZATZIKI_AUTO_FORMAT)
                editor.findTable(editor.caretModel.offset)?.format()
        }
    }

    private class TabHandler(actionId : String) : AbstractWriteActionHandler(actionId) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!TZATZIKI_AUTO_FORMAT || !editor.navigateInTableWithTab(getActionId() == ACTION_EDITOR_TAB, editor))
                doDefault(editor, caret, dataContext)
        }
    }

    private class EnterHandler : AbstractWriteActionHandler(ACTION_EDITOR_ENTER) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!TZATZIKI_AUTO_FORMAT || !editor.navigateInTableWithEnter())
                if (!TZATZIKI_AUTO_FORMAT || !editor.addTableRow())
                    doDefault(editor, caret, dataContext)
        }
    }

    private class CopyHandler : AbstractWriteActionHandler(ACTION_EDITOR_COPY) {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (!TZATZIKI_SMART_COPY || !editor.smartCopy())
                doDefault(editor, caret, dataContext)
        }
    }

    abstract class AbstractWriteActionHandler(private val id: String) : EditorWriteActionHandler() {
        private val orginHandler = EditorActionManager.getInstance().getActionHandler(id)
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext)
            = doDefault(editor, caret, dataContext)
        open fun doDefault(editor: Editor, caret: Caret?, dataContext: DataContext?)
            = orginHandler.execute(editor, caret, dataContext)
        fun getActionId()
            = id
    }

    companion object {
        private var handlerInitialized = false
    }

}

private fun EditorActionManager.replaceHandler(handler: AbstractWriteActionHandler) {
    setActionHandler(handler.getActionId(), handler)

}
