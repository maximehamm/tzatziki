package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.TZATZIKI_AUTO_FORMAT
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class ToggleTzatzikiFormattingAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent)
        = TZATZIKI_AUTO_FORMAT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        TZATZIKI_AUTO_FORMAT = !TZATZIKI_AUTO_FORMAT
    }

    override fun update(event: AnActionEvent) {
        val isVisible = event.getData(CommonDataKeys.PSI_FILE)?.fileType == GherkinFileType.INSTANCE
        event.presentation.isVisible = isVisible
        event.presentation.isEnabled = isVisible
        super.update(event)
    }

    override fun isDumbAware()
        = true
}