package io.nimbly.tzatziki

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import io.nimbly.tzatziki.TzTModuleListener.Companion.EDITOR_UNINDENT_SELECTION
import io.nimbly.tzatziki.format.createEditorContext
import io.nimbly.tzatziki.format.getDocument
import io.nimbly.tzatziki.format.getIndexOf
import org.apache.log4j.Logger
import org.jetbrains.kotlin.cli.jvm.compiler.registerExtensionPointAndExtensionsEx
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage
import org.junit.Assert
import org.junit.Ignore
import java.awt.event.InputEvent
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
        //PsiFileFactory.getInstance(project).createFileFromText(GherkinLanguage.INSTANCE, t)

        configuredFile = myFixture.configureByText("$featureName.feature", t)

        assertEquals(configuredFile!!.fileType, GherkinFileType.INSTANCE)

//        configuredFile = myFixture.configureByText(GherkinFileType.INSTANCE, t)
    }

    protected open fun checkContent(expected: String) {
        checkContent(this.configuredFile!!, expected.trimIndent())
    }

    protected open fun checkContent(clazz: PsiClass, expected: String) {
        checkContent(clazz.containingFile, expected)
    }

    protected open fun checkContent(file: PsiFile, expected: String) {

        val exp = expected.replace("`".toRegex(), "\"")
        val document = getDocument(file)!!
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
                ${s.replace(" ".toRegex(), ".")}
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

        var insertString = insertString
        var lookFor = lookFor
        insertString = insertString.toString().replace("`".toRegex(), "\"")
        if (lookFor != null) {
            lookFor = lookFor.replace("`".toRegex(), "\"")
            val indexOf: Int = getIndexOf(configuredFile!!.text, lookFor)
            assert(indexOf >= 0)
            moveCarretTo(indexOf)
        }
        val project = myFixture.project
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        WriteCommandAction.runWriteCommandAction(project, "Format table", "Tmar",
            {
                for (element in insertString) {
                    performTypingAction(myFixture.editor, element)
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }

                // mark for undo
                CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
            })
    }

    open fun performTypingAction(editor: Editor, c: Char) {
        when (c) {
            TAB -> executeAction(editor, ACTION_EDITOR_TAB)
            BACK -> executeAction(editor, EDITOR_UNINDENT_SELECTION)
            ENTER -> executeAction(editor, ACTION_EDITOR_ENTER)
            BACKSPACE_FAKE_CHAR -> executeAction(editor, ACTION_EDITOR_BACKSPACE)
            DELETE_FAKE_CHAR -> executeAction(editor, ACTION_EDITOR_DELETE)
            else -> {
                myFixture.type(c)
                TzTypedHandler().charTyped(c, project, editor, configuredFile!!)
           }
        }
    }

    open fun executeAction(editor: Editor, actionId: String) {
        executeAction(editor, actionId, false)
    }

    open fun executeAction(editor: Editor, actionId: String, assertActionIsEnabled: Boolean) {
        val actionManager = ActionManagerEx.getInstanceEx()
        val action = actionManager.getAction(actionId)
        Assert.assertNotNull(action)
        val event = AnActionEvent.createFromAnAction(
            action,
            null as InputEvent?,
            "",
            createEditorContext(editor)
        )
        action.beforeActionPerformedUpdate(event)
        if (!event.presentation.isEnabled) {
            Assert.assertFalse("Action $actionId is disabled", assertActionIsEnabled)
        } else {
            actionManager.fireBeforeActionPerformed(action, event.dataContext, event)
            action.actionPerformed(event)
            actionManager.fireAfterActionPerformed(action, event.dataContext, event)
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

    override fun getTestDataPath(): String? {
        return "src/test/resources"
    }

    override fun setUp() {
        super.setUp()

    }
}