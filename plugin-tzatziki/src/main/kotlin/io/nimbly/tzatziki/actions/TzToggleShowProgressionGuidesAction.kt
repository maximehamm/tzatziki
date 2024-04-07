package io.nimbly.tzatziki.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.run.cucumberExecutionTracker

private val KEY = "io.nimbly.tzatziki.execution.ShowProgressionGuides"

class TzToggleShowProgressionGuidesAction : ToggleAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

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
