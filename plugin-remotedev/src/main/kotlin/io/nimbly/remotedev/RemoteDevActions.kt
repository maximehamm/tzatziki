/*
 * WSL REMOTE COMPANION
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.remotedev

import com.intellij.ide.actions.CopyPathProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

/** The file the action targets — the selected/focused virtual file, if any. */
private fun AnActionEvent.targetFile(): VirtualFile? = getData(CommonDataKeys.VIRTUAL_FILE)

private fun runOffEdt(block: () -> Unit) = ApplicationManager.getApplication().executeOnPooledThread(block)

/** Reveal the selected file (or open the folder) in Windows Explorer — restores the action that
 *  IntelliJ hides on a WSL Remote Development backend. */
class OpenInWindowsExplorerAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = WslSupport.isWslBackend && e.targetFile() != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.targetFile() ?: return
        runOffEdt { WslSupport.revealInExplorer(file.path, file.isDirectory) }
    }
}

/** Open the selected file with its associated Windows application. */
class OpenInWindowsAppAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val file = e.targetFile()
        e.presentation.isEnabledAndVisible = WslSupport.isWslBackend && file != null && !file.isDirectory
    }
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.targetFile() ?: return
        runOffEdt { WslSupport.openInWindowsApp(file.path) }
    }
}

/** Copy the selected file's Windows path to the clipboard (entry in the "Copy Path/Reference…" popup). */
class CopyWindowsPathAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val file = e.targetFile()
        val visible = WslSupport.isWslBackend && file != null
        e.presentation.isEnabledAndVisible = visible
        // Feed the grey preview shown next to the entry in the "Copy Path/Reference…" popup —
        // the popup renderer reads this client-property from the presentation. update() runs on
        // the BGT (getActionUpdateThread), so resolving the path with wslpath here is safe.
        e.presentation.putClientProperty(
            CopyPathProvider.QUALIFIED_NAME,
            if (visible) WslSupport.toWindowsPath(file!!.path) else null,
        )
    }
    override fun actionPerformed(e: AnActionEvent) {
        val path = e.targetFile()?.path ?: return
        runOffEdt {
            val win = WslSupport.toWindowsPath(path) ?: return@runOffEdt
            ApplicationManager.getApplication().invokeLater {
                CopyPasteManager.getInstance().setContents(StringSelection(win))
            }
        }
    }
}
