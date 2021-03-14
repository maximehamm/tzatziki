package io.nimbly.tzatziki.format

import com.intellij.ide.DataManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil

fun getIndexOf(contents: String, lookFor: String): Int {
    val i = contents.indexOf(lookFor)
    return if (i < 0) i else i + lookFor.length
}