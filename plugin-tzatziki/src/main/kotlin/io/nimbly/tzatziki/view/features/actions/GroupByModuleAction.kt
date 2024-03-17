package io.nimbly.tzatziki.view.features.actions

import icons.ActionIcons
import io.nimbly.tzatziki.services.tzFileService
import io.nimbly.tzatziki.view.features.FeaturePanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

@Suppress("MissingActionUpdateThread")
class GroupByModuleAction(val panel: FeaturePanel) : ToggleAction() {
    init {
        this.templatePresentation.text = "Group by modules"
        this.templatePresentation.icon = ActionIcons.GROUP_BY_MODULE
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        return !panel.project.tzFileService().groupTag
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        panel.groupByTag(!state)
        panel.project.tzFileService().groupTag = !state
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}