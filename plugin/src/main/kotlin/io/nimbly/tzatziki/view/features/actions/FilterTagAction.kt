package io.nimbly.tzatziki.view.features.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.services.tagService
import io.nimbly.tzatziki.view.features.FeaturePanel

@Suppress("MissingActionUpdateThread")
class FilterTagAction(val panel: FeaturePanel) : ToggleAction() {
    init {
        this.templatePresentation.text = "Filter per tags"
        this.templatePresentation.icon = ActionIcons.FILTER
    }
    override fun isSelected(e: AnActionEvent): Boolean {
        return panel.project.tagService().filterByTags
    }
    override fun setSelected(e: AnActionEvent, state: Boolean) {

        val tagService = panel.project.tagService()

        val exp: Expression?
        if (state) {
            exp = tagService.tagExpression()
        } else {
            exp = null
        }

        tagService.filterByTags = state
        tagService.updateTagsFilter(exp)

        panel.filterByTag(state)
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}