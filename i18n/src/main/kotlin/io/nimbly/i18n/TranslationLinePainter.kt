package io.nimbly.i18n

import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import java.awt.Font

class TranslationLinePainter : EditorLinePainter() {

    override fun getLineExtensions(
        project: Project,
        file: VirtualFile,
        lineNumber: Int
    ): MutableCollection<LineExtensionInfo> {

//        val info = LineExtensionInfo(
//            "  Test", Color.GRAY, null, null, Font.PLAIN)
//
//        return mutableListOf(info)

        return mutableListOf()
    }
}