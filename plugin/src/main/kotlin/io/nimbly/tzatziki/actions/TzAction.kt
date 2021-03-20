package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

abstract class TzAction : AnAction() {

    override fun update(event: AnActionEvent) {
        val isVisible = event.getData(CommonDataKeys.PSI_FILE)?.fileType == GherkinFileType.INSTANCE
        event.presentation.isVisible = isVisible
        super.update(event)
    }

}