package io.nimbly.tzatziki.view.features.actions

import io.nimbly.tzatziki.services.tagService
import io.nimbly.tzatziki.view.features.FeaturePanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

@Suppress("MissingActionUpdateThread")
class GroupByModuleAction(val panel: FeaturePanel) : ToggleAction() {
    init {
        this.templatePresentation.text = "Group by modules"
        this.templatePresentation.icon = AllIcons.Actions.GroupByModule
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        return !panel.project.tagService().groupTag
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        panel.groupByTag(!state)
        panel.project.tagService().groupTag = !state
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}