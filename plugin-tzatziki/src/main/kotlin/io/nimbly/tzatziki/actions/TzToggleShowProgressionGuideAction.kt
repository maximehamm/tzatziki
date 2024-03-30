package io.nimbly.tzatziki.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.impl.settings.ShowBreakpointsOverLineNumbersAction
import io.nimbly.tzatziki.breakpoints.cucumberExecutionTracker

private val KEY = "io.nimbly.tzatziki.show-progression-guides"

class TzToggleShowProgressionGuideAction : ShowBreakpointsOverLineNumbersAction() { //ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean {
        return PropertiesComponent.getInstance().getInt(KEY, 1) == 1
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        PropertiesComponent.getInstance().setValue(KEY, if (state) 1 else 0, 1)
        if (!state) {
            e.project?.cucumberExecutionTracker()?.removeProgressionGuides()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = true
    }
}

fun isShowProgressionGuide()
    = PropertiesComponent.getInstance().getInt(KEY, 1) == 1
