package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.TZATZIKI_AUTO_FORMAT
import io.nimbly.tzatziki.TZATZIKI_SMART_COPY
import io.nimbly.tzatziki.util.TmarSelectionModeManager.disableColumnSelectionMode
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class ToggleTzatzikiSmartCopyAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent)
        = TZATZIKI_SMART_COPY

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        TZATZIKI_SMART_COPY = !TZATZIKI_SMART_COPY
        if (!TZATZIKI_SMART_COPY)
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