package io.nimbly.tzatziki.view.features.actions

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import icons.ActionIcons
import io.nimbly.tzatziki.view.features.*
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaFeatureRunConfigurationProducer
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaScenarioRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import javax.swing.tree.DefaultMutableTreeNode

@Suppress("MissingActionUpdateThread")
class RunTestAction(val panel: FeaturePanel) : AnAction() {
    init {
        this.templatePresentation.text = "Run Cucumber tests..."
        this.templatePresentation.icon = ActionIcons.RUN
    }
    @OptIn(IDEAPluginsCompatibilityAPI::class)
    override fun actionPerformed(e: AnActionEvent) {

        val project = panel.project
        val component = panel.tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            ?: return

        val userObject = component.userObject
        val isFeature = userObject is FeatureNode
        val isScenario = userObject is ScenarioNode
        if (isFeature || isScenario) {

            val value = (userObject as AbstractNode<*>).value
            val psiElement: PsiElement = (value as? GherkinFeature) ?: (value as GherkinStepsHolder)
            val file = psiElement.containingFile
            val module = ModuleUtilCore.findModuleForPsiElement(psiElement)
                ?: return

            val dataContext = MyDataContext()
            dataContext.put(CommonDataKeys.PROJECT, project)
            dataContext.put(CommonDataKeys.PSI_FILE, file)
            dataContext.put(PlatformCoreDataKeys.MODULE, module)
            dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement.firstChild))
            val configurationContext: ConfigurationContext = ConfigurationContext.getFromContext(dataContext)

            val runConfProds = RunConfigurationProducer.getProducers(project)
            val producer =
                if (isFeature)
                    runConfProds.find { it.javaClass == CucumberJavaFeatureRunConfigurationProducer::class.java}
                else
                    runConfProds.find { it.javaClass == CucumberJavaScenarioRunConfigurationProducer::class.java}
            val configurationFromContext = producer?.createConfigurationFromContext(configurationContext)

            if (configurationFromContext != null) {
                val runnerAndConfigurationSettings = configurationFromContext.configurationSettings
                val executor = DefaultRunExecutor.getRunExecutorInstance()
                ExecutionUtil.runConfiguration(runnerAndConfigurationSettings, executor)
            }
        }
    }

    override fun update(e: AnActionEvent) {

        val component: DefaultMutableTreeNode? = panel.tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        val userObject: Any? = component?.userObject
        val isFeature = userObject is FeatureNode
        val isScenario = userObject is ScenarioNode

        e.presentation.isEnabled = isFeature || isScenario
    }
}