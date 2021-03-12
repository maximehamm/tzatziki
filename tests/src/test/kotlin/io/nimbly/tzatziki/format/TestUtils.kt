package io.nimbly.tzatziki.format

import com.intellij.ide.DataManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil

fun getDocument(psiElement: PsiElement): Document? {
    val containingFile = psiElement.containingFile ?: return null
    var file = containingFile.virtualFile
    if (file == null) {
        file = containingFile.originalFile.virtualFile
    }
    return if (file == null) null else
        FileDocumentManager.getInstance().getDocument(file)
}

fun getIndexOf(contents: String, lookFor: String): Int {
    val i = contents.indexOf(lookFor)
    return if (i < 0) i else i + lookFor.length
}

fun createEditorContext(editor: Editor): DataContext {
    val hostEditor: Any = if (editor is EditorWindow) editor.delegate else editor
    val map: Map<String, Any> = ContainerUtil.newHashMap(
        Pair.create(CommonDataKeys.HOST_EDITOR.name, hostEditor),
        Pair.createNonNull(CommonDataKeys.EDITOR.name, editor)
    )
    val parent = DataManager.getInstance().getDataContext(editor.contentComponent)
    return SimpleDataContext.getSimpleContext(map, parent)
}