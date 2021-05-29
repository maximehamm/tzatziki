/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import io.nimbly.tzatziki.inspections.TzDeprecatedStepInspection
import io.nimbly.tzatziki.psi.getDocument
import io.nimbly.tzatziki.util.*
import junit.framework.TestCase
import org.apache.log4j.Logger
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.junit.Ignore
import java.awt.datatransfer.DataFlavor
import java.io.File

@Ignore
abstract class AbstractTestCase : JavaCodeInsightFixtureTestCase() {

    enum class EXT { java, kt }

    private val LIB_JAVA = "/lib/rt-small.jar"
    private val LIB_JAVA_CUCUMBER = "/lib/cucumber-java-6.8.1.jar"
    private val LIB_KOTLIN = "/lib/kotlin-stdlib-1.4.30.jar"

    var configuredFile: PsiFile? = null

    val TAB = '\t'
    val BACK = '\b'
    val ENTER = '\n'
    val PIPE = '|'

    val BACKSPACE_FAKE_CHAR = '\uffff'
    val DELETE_FAKE_CHAR = '\ufffe'

    protected fun setupForJava() {
        PsiTestUtil.addLibrary(myFixture.module, getTestDataPath() + '/' + LIB_JAVA)
        PsiTestUtil.addLibrary(myFixture.module, getTestDataPath() + '/' + LIB_JAVA_CUCUMBER)
    }

    fun addClass(extension: EXT, text: String) {
        if (extension == EXT.java) {
            // Java
            myFixture.addClass(text)
        }
        else if (extension == EXT.kt) {
            // Kotlin
            val regex = """(class|interface) *([\w]+)""".toRegex()
            val className = regex.find(text.trimIndent())!!.groupValues.last()
            myFixture.configureByText("$className.kt", text)
        }
    }

    protected open fun feature(text: String) {
        val t = text.smartTrim()
        val regex = """(Feature:) *([\w]+)""".toRegex()
        val featureName = regex.find(text.smartTrim())!!.groupValues.last()

        FileTypeRegistry.getInstance().registeredFileTypes.map { it.name }
        configuredFile = myFixture.configureByText("$featureName.feature", t)
        assertEquals(GherkinFileType.INSTANCE, configuredFile!!.fileType)
    }

    protected open fun configure(extension: EXT, text: String) {

        var t = text.trimIndent().trim()

        TestCase.assertTrue(t.startsWith("package"))

        if (!t.contains("import java.lang.Boolean")) {
            t = t.substringBefore("\n") + "\n" + """
            import java.lang.Boolean;
            import java.lang.String;
            import java.lang.Character;
            import java.lang.CharSequence;
            import java.lang.Number;
            import java.lang.Double;
            import java.lang.Long;
            import java.lang.Integer;
            import java.lang.Number;
            import java.lang.Float;
            import java.lang.Character;
            """.trimIndent() + t.substringAfter(";")
        }

//        if (!t.contains("<caret>")) {
//            if (t.contains("class "))
//                t = t.substringBefore("class ") + "class <caret>" +
//                        t.substringAfter("class ")
//            else
//                t = t.substringBefore("interface ") + "interface <caret>" +
//                        t.substringAfter("interface ")
//        }

//        val regex = """(class|interface) *([\w]+)""".toRegex()
//        val className = regex.find(text.trimIndent())!!.groupValues.last()
//        configuredFile = myFixture.configureByText("$className.${extension.name}", t)

        configuredFile = myFixture.addClass(t).containingFile
    }


    protected open fun checkContent(expected: String) {
        var t = expected.smartTrim()
        checkContent(this.configuredFile!!, t)
    }

    private fun String.smartTrim() : String {
        split("\n").lastOrNull()?.let {
            if (it.isBlank()) {
                return ("$this§").trimIndent().removeSuffix("§")
            }
        }
        return trimIndent()
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
                The file (with ⭑ at diff) :
                ${s.replace(" ".toRegex(), ".").smartTrim()}
                """.smartTrim()
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
        WriteCommandAction.runWriteCommandAction(project, "Format table", "Tzatziki",
            {
                for (element in ins) {
                    pressKey(element)
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }

                // mark for undo
                CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
            })
    }

    protected fun moveTo(lookFor: String?) {
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
        myFixture.editor.selectionModel.removeSelection(true)
        myFixture.editor.caretModel.removeSecondaryCarets()
        myFixture.editor.caretModel.moveToOffset(completionOffset)
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

    protected fun navigate(string: Char, expectedCellValue: String? = null) {

        // enter keyboard
        insert(string)

        // check element content
        val offset = myFixture.editor.caretModel.offset - 1
        val cell = myFixture.editor.cellAt(offset)

        assertNotNull("Cell not found : $expectedCellValue", cell)

        if (expectedCellValue!=null)
            assertEquals(expectedCellValue, cell!!.text.trim())
    }

    fun executeHandler(handlerId: String) {
        WriteCommandAction.runWriteCommandAction(project, "Execute handler", "Tzatziki", {
            val actionManager = EditorActionManager.getInstance()
            val actionHandler = actionManager.getActionHandler(handlerId)
            actionHandler.execute(myFixture.editor, null, myFixture.editor.createEditorContext())
        })
    }

    protected open fun selectAsColumn(lookForStart: String, lookForEnd: String) {
        val start = getIndexOf(configuredFile!!.text, lookForStart)
        val end = getIndexOf(configuredFile!!.text, lookForEnd)
        selectAsColumn(start, end)
    }

    protected open fun selectAsColumn(offsetStart: Int, offsetEnd: Int) {
        assert(offsetStart >= 0)
        assert(offsetEnd >= 0)
        val start = myFixture.editor.offsetToLogicalPosition(offsetStart)
        val end = myFixture.editor.offsetToLogicalPosition(offsetEnd)

        moveCarretTo(offsetStart)
        myFixture.editor.setColumnMode(true)
        val selectionModel = myFixture.editor.selectionModel
        selectionModel.removeSelection(true)
        selectionModel.setBlockSelection(start, end)
    }

    protected open fun select(lookForSelectionStart: String, lookForSelectionEnd: String) {
        var l = lookForSelectionStart
        l = l.replace("`".toRegex(), "\"")
        val start = getIndexOf(configuredFile!!.text, l)
        assert(start > 0)

        l = lookForSelectionEnd
        l = l.replace("`".toRegex(), "\"")
        val end = getIndexOf(configuredFile!!.text, l)
        assert(end > 0)

        myFixture.editor.setColumnMode(false)
        myFixture.editor.caretModel.removeSecondaryCarets()
        moveCarretTo(end)
        myFixture.editor.selectionModel.setSelection(start, end)
    }

    protected open fun checkSelectionColumn(lookForStart: String, lookForEnd: String) {
        val offsetStart = getIndexOf(configuredFile!!.text, lookForStart)
        val offsetEnd = getIndexOf(configuredFile!!.text, lookForEnd)
        val editor = myFixture.editor

        assertTrue(offsetStart >= 0)
        assertTrue(offsetEnd >= 0)
        //assertTrue(editor.isColumnMode)

        val selectionStart = editor.selectionModel.blockSelectionStarts.firstOrNull()
        val selectionEnd = editor.selectionModel.blockSelectionEnds.lastOrNull()

        assertEquals(offsetStart, selectionStart)
        assertEquals(offsetEnd, selectionEnd)
    }

    protected open fun checkSelectionEmpty() {
        assertTrue(!myFixture.editor.selectionModel.hasSelection())
    }

    protected open fun copy() {
        executeHandler(ACTION_EDITOR_COPY)
    }

    protected open fun cut() {
        executeHandler(ACTION_EDITOR_CUT)
    }

    protected open fun delete(repeat: Int=1) {
        for (i in 0 until repeat)
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
        assertEquals(expected.smartTrim(), found)
    }

    protected fun checkHighlighted(lookForStart: String, lookForEnd: String) {
        val start = getIndexOf(configuredFile!!.text, lookForStart)
        val end = getIndexOf(configuredFile!!.text, lookForEnd)

        assert(start > 0)
        assert(start < end)


        val ranges = mutableListOf<TextRange>()
        myFixture.editor.document.let {
            val colStart = it.getColumnAt(start)
            val width = it.getColumnAt(end) - colStart
            val lineStart = it.getLineNumber(start)
            val lineEnd = it.getLineNumber(end)
            for (line in lineStart..lineEnd) {
                val start1 = it.getLineStartOffset(line) + colStart
                val end1 = start1 + width
                ranges.add(TextRange(start1, end1))
            }
        }

        assertEquals(ranges.size, HIGHLIGHTERS_RANGE.size)
        ranges.forEachIndexed { i, range ->
            assertEquals(range, HIGHLIGHTERS_RANGE[i])
        }
    }

    fun moveLeft() = myFixture.editor.executeAction("io.nimbly.tzatziki.ShiftLeft")
    fun moveRight() = myFixture.editor.executeAction("io.nimbly.tzatziki.ShiftRight")
    fun moveUp() = myFixture.editor.executeAction("io.nimbly.tzatziki.ShiftUp")
    fun moveDown() = myFixture.editor.executeAction("io.nimbly.tzatziki.ShiftDown")


    override fun getTestDataPath(): String? {
        return "src/test/resources"
    }

    fun enableInspections() {
        myFixture.enableInspections(TzDeprecatedStepInspection::class.java)
    }

    fun createJavaMarkers(): List<IMarker> {
        enableInspections()
        myFixture.openFileInEditor(configuredFile!!.virtualFile)
        return reloadMarkers()
    }

    protected open fun reloadMarkers(): List<IMarker> {

        // TRICKY : commit previous write operations
        // (Otherwise when launching all tests suite, strange error occurs...)
        val project = module.project
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(myFixture.editor.document)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Gets hightlightings
        val highlightInfos = myFixture.doHighlighting()
        myFixture.availableIntentions

        // Convert to IMarkers
        val markers = mutableListOf<IMarker>()
        for (hi in highlightInfos) {
            val d = hi.description ?: continue
            //if (d.startsWith("Cannot resolve method 'iterator()'")) continue
            markers.add(Marker(hi))
        }
        return markers
    }

    protected open fun markerExists(markers: List<IMarker>, name: String): List<IMarker> {
        return markerExists(markers, name, 1)
    }

    protected open fun markerExists(markers: List<IMarker>, name: String, count: Int): List<IMarker> {
        var c = 0
        val found = mutableListOf<IMarker>()
        markers.forEach { m ->
            if (name == m.message) {
                c++
                found.add(m)
            }
        }
        if (c != count) {
            val sb = StringBuffer()
            markers.forEach { m -> sb.append("\n" + m.message) }
            if (c == 0) {
                fail("Expected to find IMarker :\n-->$name\n but not found...\nAll IMarkers: $sb")
            } else {
                fail(
                    "Expected to find " + count + " instances of marker '" + name + "' but "
                            + (if (c == 0) "not found." else "found $c")
                            + "\n  All IMarkers: " + sb
                )
            }
        }
        return found
    }

    protected open fun markerNotExists(markers: List<IMarker>, name: String) {
        val found = markers
            .asSequence()
            .filterNotNull()
            .filter { name == it.message }
            .toList()
        if (found.isEmpty()) return
        val sb = StringBuffer()
        markers
            .filterNotNull()
            .forEach { sb.append("\n" + it.message) }
        fail("Expected no marker IMarker :\n-->$name\n but found...\nAll IMarkers: $sb")
    }

    protected open fun markersEmpty(markers: List<IMarker>, vararg excludedProblems: String) {
        markersCount(markers, 0, *excludedProblems)
    }

    /**
     * markersCount
     *
     * @author Maxime HAMM
     */
    protected open fun markersCount(markers: List<IMarker>, expectedCount: Int, vararg excludedProblems: String) {
        if (markers.isEmpty() && expectedCount > 0) {
            fail("Expected $expectedCount IMarker, but none !")
            return
        }
        val excluded = setOf(*excludedProblems)
        var count = 0
        val sb = StringBuffer()
        markers.forEach { m: IMarker ->
            val ex = excluded.contains(m.problemGroup)
            if (m.problemGroup != null && ex) return@forEach
            sb.append('\n')
            if (ex) sb.append('(')
            sb.append(m.message)
            if (ex) sb.append(')')
            count++
        }
        if (count != expectedCount) {
            fail("Expected $expectedCount IMarker, but : $sb")
        }
    }
}

fun getIndexOf(contents: String, lookFor: String): Int {
    val i = contents.indexOf(lookFor)
    return if (i < 0) i else i + lookFor.length
}