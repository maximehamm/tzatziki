package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.SMART_EDIT
import io.nimbly.tzatziki.util.TzSelectionModeManager.disableColumnSelectionMode
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class ToggleTzatzikiSmartCopyAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent)
        = SMART_EDIT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        SMART_EDIT = !SMART_EDIT
        if (!SMART_EDIT)
            e.getData(CommonDataKeys.EDITOR)?.disableColumnSelectionMode()
    }

    override fun update(event: AnActionEvent) {
        val isVisible = event.getData(CommonDataKeys.PSI_FILE)?.fileType == GherkinFileType.INSTANCE
        event.presentation.isEnabledAndVisible = isVisible
        super.update(event)
    }

    override fun isDumbAware()
        = true
}