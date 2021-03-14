package io.nimbly.tzatziki.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.editor.event.CaretAdapter
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.EditorMouseAdapter
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.tzatziki.TZATZIKI_SMART_COPY
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseEvent
import java.io.StringReader

fun Editor.smartCopy(): Boolean {

    val offset = caretModel.offset
    val table = findTable(offset) ?: return false
    if (!selectionModel.selectedText!!.contains("|")) return false

    // Prepare table content
    val transferable = TzatzikiTransferableData(table, selectionModel.blockSelectionStarts, selectionModel.blockSelectionEnds)

    // Add to clipboard
    CopyPasteManager.getInstance().setContents(transferable)
    return true
}

fun getTransferable(table: GherkinTable, dataFlavor: DataFlavor, starts: IntArray, ends: IntArray): String {

    val raw = dataFlavor == RawText.getDataFlavor()
    val sb = StringBuilder()

    for (i in starts.indices) {

        val r = TextRange(starts[i], ends[i])
        val cells: List<GherkinTableCell> = table.cellsInRange(r)

        if (cells.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append('\n')

        for (j in cells.indices) {

            val c = cells[j]
            var inter = c.textRange.intersection(r)

            if (inter != null) {
                inter = inter.shiftRight(-c.textOffset)
                sb.append(if (raw) inter.substring(c.text) else c.text.trim())
            }

            if (j < cells.size-1)
                sb.append('\t')
        }
    }
    return sb.toString()
}

@Suppress("JoinDeclarationAndAssignment")
class TzatzikiTransferableData(table: GherkinTable, startOffsets: IntArray, endOffsets: IntArray) : Transferable {

    private val myTransferDataFlavors: List<DataFlavor>
    private val text: String
    private val textAsRaw: RawText

    override fun getTransferDataFlavors()
        = myTransferDataFlavors.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor)
        = transferDataFlavors.find { it == flavor } != null

    override fun getTransferData(flavor: DataFlavor): Any {
        try {
            when {
                Comparing.equal(RawText.getDataFlavor(), flavor) -> return textAsRaw
                DataFlavor.stringFlavor.equals(flavor) -> return text
                DataFlavor.plainTextFlavor.equals(flavor) -> return StringReader(text)
            }
        } catch (e: NoClassDefFoundError) {
        }
        throw UnsupportedFlavorException(flavor)
    }

    init {
        myTransferDataFlavors = listOfNotNull(
            DataFlavor.stringFlavor, DataFlavor.plainTextFlavor, RawText.getDataFlavor()
        )
        textAsRaw = RawText(getTransferable(table, RawText.getDataFlavor(), startOffsets, endOffsets))
        text = getTransferable(table, DataFlavor.stringFlavor, startOffsets, endOffsets)
    }
}

fun manageDoubleClicTableSelection(table: GherkinTable, editor: Editor, offset: Int): Boolean {

    if (!TZATZIKI_SMART_COPY)
        return false

    val cell = table.cellAt(offset) ?: return false
    val selectionModel = editor.selectionModel

    val row = table.rowAt(offset)!!
    if (row.isHeader()) {

        val index: Int = row.psiCells.indexOf(cell)
        val rows = table.allRows()
        val first = rows[0]
        val last = rows[rows.size - 1]
        if (first !== last) {
            var cells= first.psiCells

            val firstCell = if (cells.size > index) cells[index] else null
            cells = last.psiCells

            val lastCell = if (cells.size > index) cells[index] else null
            if (firstCell != null && lastCell != null) {

                val start = editor.offsetToLogicalPosition(firstCell.textOffset-1)
                val end = editor.offsetToLogicalPosition(lastCell.nextPipe().textRange.startOffset)
                val caretStates = EditorModificationUtil.calcBlockSelectionState(editor, start, end)

                editor.caretModel.setCaretsAndSelections(caretStates, false)
                return true
            }
        }
    }

    // Select line
    editor.setColumnMode(true)

    val margin = 0
    selectionModel.setSelection(row.startOffset, row.endOffset - margin)
    return true
}

object TmarSelectionModeManager {

    var isSelectionSwitchBlocked = false
        private set

    fun switchEditorSelectionModeIfNeeded(editor: Editor, offset: Int) {

        if (isSelectionSwitchBlocked) return
        val f = editor.getFile() ?: return
        if (f !is GherkinFile) return

        val file = f as GherkinFile
        val needColumnMode = file.isColumnSeletionModeZone(offset)
        if (needColumnMode && !editor.isColumnMode
            || !needColumnMode && editor.isColumnMode) {

            try {
                editor.executeAction("EditorToggleColumnMode")
            } catch (ignored: Exception) {
            }
        }
    }

    fun Editor.disableColumnSelectionMode() {
        if (isColumnMode) {
            try {
                executeAction("EditorToggleColumnMode")
            } catch (ignored: Exception) {
            }
        }
    }

    fun blockSelectionSwitch() {
        isSelectionSwitchBlocked = true
    }

    fun releaseSelectionSwitch() {
        isSelectionSwitchBlocked = false
    }
}

object TZMouseAdapter : EditorMouseAdapter() {
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

object TZCaretAdapter : CaretAdapter() {
    override fun caretPositionChanged(e: CaretEvent) {

        if (!TZATZIKI_SMART_COPY)
            return

        val editor = e.editor
        val offset = editor.logicalPositionToOffset(e.newPosition)
        TmarSelectionModeManager.switchEditorSelectionModeIfNeeded(editor, offset)
    }
}

