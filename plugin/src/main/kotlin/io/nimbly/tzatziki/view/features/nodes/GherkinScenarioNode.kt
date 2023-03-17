package io.nimbly.tzatziki.view.features.nodes

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.CucumberPlusDataKeys
import io.nimbly.tzatziki.util.TzDataContext
import io.nimbly.tzatziki.util.getModule
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaScenarioRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

class GherkinScenarioNode(p: Project, scenario: GherkinStepsHolder, exp: Expression?) : AbstractTzPsiElementNode<GherkinStepsHolder>(p, scenario, exp),
    TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.scenarioName.trim().ifEmpty { "Scenario" }
        presentation.setIcon(ActionIcons.STEP)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf()
    }

    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
        val runConfProds = RunConfigurationProducer.getProducers(project)
        return runConfProds.find { it.javaClass == CucumberJavaScenarioRunConfigurationProducer::class.java }
    }

    override fun getRunDataContext(): ConfigurationContext {
        val context = TzDataContext()
        context.put(CommonDataKeys.PROJECT, project)
        context.put(CommonDataKeys.PSI_FILE, value.containingFile)
        context.put(CucumberPlusDataKeys.MODULE, value.getModule())
        context.put(Location.DATA_KEY, PsiLocation.fromPsiElement(value.firstChild))
        return context.configutation()
    }

    override fun getRunActionText() = "Run scenario..."

}