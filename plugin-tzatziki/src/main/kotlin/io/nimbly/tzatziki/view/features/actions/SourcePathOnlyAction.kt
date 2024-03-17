package io.nimbly.tzatziki.view.features.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.IconManager
import io.nimbly.tzatziki.services.tzFileService
import io.nimbly.tzatziki.view.features.FeaturePanel

@Suppress("MissingActionUpdateThread")
class SourcePathOnlyAction(val panel: FeaturePanel) : ToggleAction() {
    init {
        this.templatePresentation.text = "Source and resource path only"
        this.templatePresentation.icon = AllIcons.Modules.ResourcesRoot
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        return panel.project.tzFileService().sourcePathOnly
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        panel.sourcePathOnly(state)
        panel.project.tzFileService().sourcePathOnly = state
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}