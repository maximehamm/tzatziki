package io.nimbly.tzatziki.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.breakpoints.cucumberExecutionTracker

private val KEY = "io.nimbly.tzatziki.execution.ShowProgressionGuides"

@Suppress("MissingActionUpdateThread")
class TzToggleShowProgressionGuidesAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean {
        return PropertiesComponent.getInstance().getBoolean(KEY, true)
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        PropertiesComponent.getInstance().setValue(KEY, state, true)
        if (!state) {
            e.project?.cucumberExecutionTracker()?.removeProgressionGuides()
        }
    }

    override fun update(e: AnActionEvent) {
       super.update(e)
    }
}

fun isShowProgressionGuide()
    = PropertiesComponent.getInstance().getBoolean(KEY, true)
