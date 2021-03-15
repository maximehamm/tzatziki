package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import io.nimbly.tzatziki.format.getIndexOf
import io.nimbly.tzatziki.util.cellAt
import io.nimbly.tzatziki.util.createEditorContext
import io.nimbly.tzatziki.util.executeAction
import io.nimbly.tzatziki.util.getDocument
import org.apache.log4j.Logger
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.junit.Ignore
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File

@Ignore
abstract class AbstractTestCase : JavaCodeInsightFixtureTestCase() {

    var configuredFile: PsiFile? = null

    val TAB = '\t'
    val BACK = '\b'
    val ENTER = '\n'
    val PIPE = '|'

    val BACKSPACE_FAKE_CHAR = '\uffff'
    val DELETE_FAKE_CHAR = '\ufffe'

    fun String.configure(): String {
        configure(this)
        return this
    }

    protected open fun configure(text: String) {
        val t = text.trimIndent().trim()
        val regex = """(Feature:) *([\w]+)""".toRegex()
        val featureName = regex.find(text.trimIndent())!!.groupValues.last()

        FileTypeRegistry.getInstance().registeredFileTypes.map { it.name }
        configuredFile = myFixture.configureByText("$featureName.feature", t)
        assertEquals(GherkinFileType.INSTANCE, configuredFile!!.fileType)
    }

    protected open fun checkContent(expected: String) {
        checkContent(this.configuredFile!!, expected.trimIndent())
    }

    protected open fun checkContent(clazz: PsiClass, expected: String) {
        checkContent(clazz.containingFile, expected)
    }

    protected open fun checkContent(file: PsiFile, expected: String) {

        val exp = expected.replace("`".toRegex(), "\"")
        val document = file.getDocument()!!
        val project = myFixture.project
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val text = file.text
        Logger.getLogger(javaClass).info("\n*****************\n$text")
        val diff = indexOfDifference(text, exp)
        if (diff >= 0) {
            var s = ""
            if (diff >= 0) {
                s = text.substring(0, diff)
            }
            s += "⭑"
            if (diff < text.length) {
                s += text.substring(diff)
            }
            fail(
                """
                Expected file content not found.
                The file (with ⭑ at diff) :\n
                ${s.replace(" ".toRegex(), " ").trimIndent()}
                """.trimIndent()
            )
        }
    }

    open fun checkCursorAt(lookFor: String) {
        var l = lookFor
        l = l.replace("`".toRegex(), "\"")
        val indexOf: Int = getIndexOf(configuredFile!!.text, l)
        val offset = myFixture.editor.caretModel.offset
        assertEquals(indexOf, offset)
    }

    private fun indexOfDifference(str1: String?, str2: String?): Int {
        if (str1 === str2) {
            return -1
        }
        if (str1 == null || str2 == null) {
            return 0
        }
        var i: Int
        i = 0
        while (i < str1.length && i < str2.length) {
            if (str1[i] != str2[i]) {
                break
            }
            ++i
        }
        return if (i < str2.length || i < str1.length) {
            i
        } else -1
    }

    protected open fun insert(vararg insertChar: Char) {
        insert(StringBuilder().append(insertChar), null)
    }

    protected open fun insert(insertString: String) {
        insert(insertString, null)
    }

    protected open fun insert(insertChar: Char, lookFor: String?) {
        insert(StringBuilder().append(insertChar), lookFor)
    }

    @Suppress("NAME_SHADOWING")
    protected open fun insert(insertString: CharSequence, lookFor: String?) {

        val ins = insertString.toString().replace("`".toRegex(), "\"")
        moveTo(lookFor)
        
        val project = myFixture.project
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        WriteCommandAction.runWriteCommandAction(project, "Format table", "Tmar",
            {
                for (element in ins) {
                    pressKey(element)
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }

                // mark for undo
                CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
            })
    }

    private fun moveTo(lookFor: String?) {
        if (lookFor != null) {
            val lf = lookFor.replace("`".toRegex(), "\"")
            val indexOf: Int = getIndexOf(configuredFile!!.text, lf)
            assert(indexOf >= 0)
            moveCarretTo(indexOf)
        }
    }

    open fun pressKey(c: Char, lookFor: String? = null) {

        moveTo(lookFor)

        val editor = myFixture.editor
        when (c) {
            TAB -> editor.executeAction(ACTION_EDITOR_TAB)
            BACK -> editor.executeAction(EDITOR_UNINDENT_SELECTION)
            ENTER -> editor.executeAction(ACTION_EDITOR_ENTER)
            BACKSPACE_FAKE_CHAR -> editor.executeAction(ACTION_EDITOR_BACKSPACE)
            DELETE_FAKE_CHAR -> editor.executeAction(ACTION_EDITOR_DELETE)
            else -> {
                myFixture.type(c)
                TzTypedHandler().charTyped(c, project, editor, configuredFile!!)
           }
        }
    }

    open fun moveCarretTo(completionOffset: Int) {
        myFixture.editor.caretModel.moveToOffset(completionOffset)
        myFixture.editor.selectionModel.removeSelection(true)
    }

    protected open fun findFileInTempDir(filePath: String): VirtualFile? {
        val fullPath = myFixture.tempDirPath + "/" + filePath
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'))
    }

    protected open fun backspace(count: Int) {
        _backspace(count)
    }

    protected open fun backspace(count: Int, lookFor: String) {
        val indexOf: Int = getIndexOf(configuredFile!!.text, lookFor)
        moveCarretTo(indexOf)
        _backspace(count)
    }

    open fun _backspace(count: Int) {
        for (i in 0 until count) {
            executeHandler(ACTION_EDITOR_BACKSPACE)
        }
    }

    protected open fun setCursor(lookFor: String) {
        var l = lookFor
        l = l.replace("`".toRegex(), "\"")
        val indexOf: Int = getIndexOf(configuredFile!!.text, l)
        assert(indexOf >= 0)
        moveCarretTo(indexOf)
    }

    protected fun navigate(string: Char, expectedCellValue: String) {

        // enter keyboard
        insert(string)

        // check element content
        val offset = myFixture.editor.caretModel.offset - 1
        val cell = myFixture.editor.cellAt(offset)

        assertNotNull("Cell not found : $expectedCellValue", cell)
        assertEquals(expectedCellValue, cell!!.text.trim())
    }

    fun executeHandler(handlerId: String) {
        WriteCommandAction.runWriteCommandAction(project, "Execute handler", "Tzatziki", {
            val actionManager = EditorActionManager.getInstance()
            val actionHandler = actionManager.getActionHandler(handlerId)
            actionHandler.execute(myFixture.editor, myFixture.editor.createEditorContext())
        })
    }

    protected open fun selectAsColumn(lookForStart: String, lookForEnd: String) {
        val start: Int = getIndexOf(configuredFile!!.text, lookForStart)
        val end: Int = getIndexOf(configuredFile!!.text, lookForEnd)
        selectAsColumn(start, end)
    }

    protected open fun selectAsColumn(offsetStart: Int, offsetEnd: Int) {
        assert(offsetStart >= 0)
        assert(offsetEnd >= 0)
        val start = myFixture.editor.offsetToLogicalPosition(offsetStart)
        val end = myFixture.editor.offsetToLogicalPosition(offsetEnd)
        moveCarretTo(offsetStart)
        val selectionModel = myFixture.editor.selectionModel
        selectionModel.removeSelection(true)
        selectionModel.setBlockSelection(start, end)
    }

    protected open fun select(lookForSelectionStart: String, selectedText: String) {
        var l = lookForSelectionStart
        l = l.replace("`".toRegex(), "\"")
        val indexOf = getIndexOf(configuredFile!!.text, l)
        assert(indexOf > 0)

        moveCarretTo(indexOf + selectedText.length)
        select(indexOf, selectedText.length)
    }
    protected open fun select(offset: Int, length: Int) {
        myFixture.editor.selectionModel.setSelection(offset, offset + length)
    }

    protected open fun copy() {
        executeHandler(ACTION_EDITOR_COPY)
    }

    protected open fun delete() {
        executeHandler(ACTION_EDITOR_DELETE)
    }

    protected open fun backspace() {
        executeHandler(ACTION_EDITOR_BACKSPACE)
    }

    protected open fun paste() {
        executeHandler(ACTION_EDITOR_PASTE)
    }

    protected fun checkClipboard(expected: String) {
        val found = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertEquals(expected.trimIndent(), found)
    }

    override fun getTestDataPath(): String? {
        return "src/test/resources"
    }

}