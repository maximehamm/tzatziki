package io.nimbly.tzatziki.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import io.nimbly.tzatziki.breakpoints.TzExecutionCucumberListener.Companion.cucumberExecutionTracker
import io.nimbly.tzatziki.util.getDocument
import io.nimbly.tzatziki.util.getDocumentLine
import io.nimbly.tzatziki.util.getFile
import io.nimbly.tzatziki.util.toPath

@Deprecated("TO REMOVE")
class TzSourcePositionProvider : SourcePositionProvider() {

    override fun computeSourcePosition(
        descriptor: NodeDescriptor,
        project: Project,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {

        val executionPoint = project.cucumberExecutionTracker()
        val featurePath = executionPoint.featurePath ?: return null
        val lineNumber = executionPoint.lineNumber ?: return null

        val vfile = LocalFileSystem.getInstance().findFileByNioFile(featurePath.toPath()) ?: return null
        val file = vfile.getFile(project) ?: return null
        val document = file.getDocument() ?: return null

        val offset = document.getLineStartOffset(lineNumber - 1)
        var elementAt = file.findElementAt(offset) ?: return null

        if (elementAt is PsiWhiteSpace)
            elementAt = elementAt.nextSibling

        return TzSourcePosition(elementAt)
    }

    @Deprecated("TO REMOVE")
    class TzSourcePosition(val element: PsiElement) : SourcePosition() {

        override fun navigate(requestFocus: Boolean) {
            //TODO
        }

        override fun canNavigate(): Boolean {
            return false
        }

        override fun canNavigateToSource(): Boolean {
            return false
        }

        override fun getFile(): PsiFile {
            return element.containingFile
        }

        override fun getElementAt(): PsiElement {
            return element
        }

        override fun getLine(): Int {
            return element.getDocumentLine() ?: 0
        }

        override fun getOffset(): Int {
            return element.textOffset
        }

        override fun openEditor(requestFocus: Boolean): Editor {
            return EditorFactory.getInstance().editors(element.getDocument()!!).findFirst().get()
        }

    }
}
