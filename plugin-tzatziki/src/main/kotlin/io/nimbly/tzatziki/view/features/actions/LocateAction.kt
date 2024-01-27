package io.nimbly.tzatziki.view.features.actions

import io.nimbly.tzatziki.view.features.FeaturePanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

@Suppress("MissingActionUpdateThread")
class LocateAction(val panel: FeaturePanel) : AnAction() {

    init {
        this.templatePresentation.text = "Select opened file"
        this.templatePresentation.icon = AllIcons.General.Locate
    }

    override fun actionPerformed(e: AnActionEvent) {
        panel.selectFromEditor()
    }

    // Compatibility : introduced 2022.2.4
    //override fun getActionUpdateThread() = ActionUpdateThread.BGT
}