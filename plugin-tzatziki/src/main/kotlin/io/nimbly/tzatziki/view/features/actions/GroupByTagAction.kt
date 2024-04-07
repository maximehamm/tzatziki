package io.nimbly.tzatziki.view.features.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.ActionIcons
import io.nimbly.tzatziki.services.tzFileService
import io.nimbly.tzatziki.view.features.FeaturePanel

class GroupByTagAction(val panel: FeaturePanel) : ToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    init {
        this.templatePresentation.text = "Group by tags"
        this.templatePresentation.icon = ActionIcons.TAG
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        return panel.project.tzFileService().groupTag
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        panel.groupByTag(state)
        panel.project.tzFileService().groupTag = state
    }
}