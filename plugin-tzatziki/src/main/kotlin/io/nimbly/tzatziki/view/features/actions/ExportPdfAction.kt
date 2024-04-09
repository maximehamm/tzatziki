package io.nimbly.tzatziki.view.features.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import icons.ActionIcons
import io.nimbly.tzatziki.pdf.ExportPdf
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.TzatzikiException
import io.nimbly.tzatziki.util.file
import io.nimbly.tzatziki.util.notification
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.AbstractTzPsiElementNode
import io.nimbly.tzatziki.view.features.nodes.GherkinTagNode
import io.nimbly.tzatziki.view.features.nodes.ModuleNode
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import io.nimbly.tzatziki.services.findAllGerkinsFiles

class ExportPdfAction(val panel: FeaturePanel) : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    init {
        this.templatePresentation.text = "Export selected features to pdf..."
        this.templatePresentation.icon = ActionIcons.PDF
    }

    override fun actionPerformed(event: AnActionEvent) {

        val project = panel.project
        val vfiles = mutableListOf<VirtualFile>()

        val paths = panel.tree.selectionPaths
        paths?.forEach {  treePath ->
            val userObject = TreeUtil.getLastUserObject(treePath)
            if (userObject is AbstractTzPsiElementNode<*>) {
                vfiles.add(userObject.value.containingFile.virtualFile)
            } else if (userObject is ModuleNode) {
                val module: Module = userObject.value
                findAllGerkinsFiles(module, true)
                    .map { it.virtualFile }
                    .forEach {
                        if (!vfiles.contains(it)) vfiles.add(it)
                    }
            } else if (userObject is GherkinTagNode) {
                userObject.children
                    .map { it.value }
                    .filterIsInstance<GherkinFile>()
                    .map { it.virtualFile }
                    .forEach {
                        if (!vfiles.contains(it)) vfiles.add(it)
                    }
            } else {
                println("Not supported yet")
            }
        }
        if (paths.isNullOrEmpty() && vfiles.isEmpty()) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val editorFile = (editor?.file as? GherkinFile)?.virtualFile
            if (editorFile != null) {
                vfiles.add(editorFile)
            }
        }

        if (vfiles.isEmpty()) {
            project.notification("Please select one or more features !", NotificationType.WARNING)
        }

        try {
            ExportPdf(vfiles, project).exportFeatures()
        } catch (e: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotification("Please wait until index is ready")
        } catch (e: TzatzikiException) {
            project.notification(e.message ?: "$TZATZIKI_NAME error !", NotificationType.WARNING)
        } catch (e: Exception) {
            e.printStackTrace()
            project.notification(e.message ?: "$TZATZIKI_NAME error !", NotificationType.WARNING)
        }
    }

    override fun update(event: AnActionEvent) {

        val isEnabled: Boolean
        val paths = panel.tree.selectionPaths
        val project = panel.project
        if (paths?.isNotEmpty() == true) {
            isEnabled = true
        }
        else {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val editorFile = (editor?.file as? GherkinFile)
            isEnabled = editorFile is GherkinFile
        }

        event.presentation.isEnabled = isEnabled

        super.update(event)
    }
}