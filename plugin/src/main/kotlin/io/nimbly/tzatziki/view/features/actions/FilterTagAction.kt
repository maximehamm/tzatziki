package io.nimbly.tzatziki.view.features.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.ServiceManager
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.services.TzTagService
import io.nimbly.tzatziki.view.features.FeaturePanel

@Suppress("MissingActionUpdateThread")
class FilterTagAction(val panel: FeaturePanel) : ToggleAction() {
    init {
        this.templatePresentation.text = "Filter per tags"
        this.templatePresentation.icon = AllIcons.General.Filter
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        val state = ServiceManager.getService(panel.project, TzPersistenceStateService::class.java)
        return state.filterByTags == true
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {

        val exp: Expression?
        if (state) {
            val stateService = ServiceManager.getService(panel.project, TzPersistenceStateService::class.java)
            exp = stateService.tagExpression()
        } else {
            exp = null
        }

        val stateService = ServiceManager.getService(panel.project, TzPersistenceStateService::class.java)
        stateService.groupTag = state

        val tagService = panel.project.getService(TzTagService::class.java)
        tagService.updateTagsFilter(exp)

        panel.filterByTag(state)
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}