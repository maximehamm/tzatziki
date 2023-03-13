package io.nimbly.tzatziki.view.features.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.ServiceManager
import icons.ActionIcons
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.view.features.FeaturePanel

@Suppress("MissingActionUpdateThread")
class GroupByTagAction(val panel: FeaturePanel) : ToggleAction() {
    init {
        this.templatePresentation.text = "Group by tags"
        this.templatePresentation.icon = ActionIcons.TAG_GRAY
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        val state = ServiceManager.getService(panel.project, TzPersistenceStateService::class.java)
        return state.groupTag == true
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        panel.groupByTag(state)

        val stateService = ServiceManager.getService(panel.project, TzPersistenceStateService::class.java)
        stateService.groupTag = state
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}