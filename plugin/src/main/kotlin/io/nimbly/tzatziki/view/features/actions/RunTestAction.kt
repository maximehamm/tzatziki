package io.nimbly.tzatziki.view.features.actions

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.ActionIcons
import io.nimbly.tzatziki.util.file
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.GherkinFileNode
import io.nimbly.tzatziki.view.features.nodes.TzRunnableNode
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import com.intellij.openapi.fileEditor.FileEditorManager
import javax.swing.tree.DefaultMutableTreeNode

private const val PRESENTATION_TEXT = "Run Cucumber..."

@Suppress("MissingActionUpdateThread")
class RunTestAction(val panel: FeaturePanel) : AnAction() {

    init {
        this.templatePresentation.text = PRESENTATION_TEXT
        this.templatePresentation.icon = ActionIcons.RUN
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!runFromTree())
            runFromEditor()
    }

    private fun runFromEditor() {
        val editor = FileEditorManager.getInstance(panel.project).selectedTextEditor
        val file = (editor?.file as? GherkinFile)
        if (file != null) {

            runFromTreeNode(
                GherkinFileNode(panel.project, file, null))
        }
    }

    private fun runFromTree(): Boolean {

        val component = panel.tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            ?: return false

        val userObject = component.userObject
        if (userObject !is TzRunnableNode)
            return false

        return runFromTreeNode(userObject)
    }

    private fun runFromTreeNode(userObject: TzRunnableNode): Boolean {

        val configurationContext = userObject.getRunDataContext()
        configurationContext.location
            ?: return false

        val producer = userObject.getRunConfiguration()
            ?: return false

        val configurationFromContext = producer.createConfigurationFromContext(configurationContext)
            ?: return false

        val runnerAndConfigurationSettings = configurationFromContext.configurationSettings
        val executor = DefaultRunExecutor.getRunExecutorInstance()

        ExecutionUtil.runConfiguration(runnerAndConfigurationSettings, executor)

        return true
    }

    override fun update(e: AnActionEvent) {

        val component: DefaultMutableTreeNode? = panel.tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        val userObject: Any? = component?.userObject

        val isEnabled : Boolean
        if (userObject is TzRunnableNode) {
            isEnabled = true
        }
        else {
            val editor = FileEditorManager.getInstance(panel.project).selectedTextEditor
            val editorFile = (editor?.file as? GherkinFile)
            isEnabled = editorFile is GherkinFile
        }

        e.presentation.isEnabled = isEnabled
        e.presentation.text = (userObject as? TzRunnableNode)?.getRunActionText() ?: PRESENTATION_TEXT
    }
}