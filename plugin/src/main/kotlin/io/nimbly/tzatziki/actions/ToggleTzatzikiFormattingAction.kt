package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import io.nimbly.tzatziki.TZATZIKI_AUTO_FORMAT
import io.nimbly.tzatziki.util.findTable
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class ToggleTzatzikiFormattingAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        TZATZIKI_AUTO_FORMAT = !TZATZIKI_AUTO_FORMAT
    }

    override fun update(event: AnActionEvent) {
        val isVisible = event.getData(CommonDataKeys.PSI_FILE)?.fileType == GherkinFileType.INSTANCE
        event.presentation.isVisible = isVisible
        event.presentation.isEnabled = isVisible
//        event.presentation.icon =
    }

    override fun isDumbAware()
        = false
}