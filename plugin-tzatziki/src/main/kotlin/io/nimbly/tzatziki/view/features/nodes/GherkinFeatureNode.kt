package io.nimbly.tzatziki.view.features.nodes

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.TzDataContext
import io.nimbly.tzatziki.util.checkExpression
import io.nimbly.tzatziki.util.getModule
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.Project

class GherkinFeatureNode(p: Project, feature: GherkinFeature, exp: Expression?) : AbstractTzPsiElementNode<GherkinFeature>(p, feature, exp),
    TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.featureName
        presentation.setIcon(AllIcons.General.ReaderMode)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return value.scenarios
            .filter { it.scenarioKeyword != "Background" }
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinScenarioNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }

    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
        try {
            val runConfProds = RunConfigurationProducer.getProducers(project)
            return runConfProds.find { it.javaClass == org.jetbrains.plugins.cucumber.java.run.CucumberJavaFeatureRunConfigurationProducer::class.java }
        } catch (e: NoClassDefFoundError) {
            // Needed to avoid crashed on GoLand, PhpStorm...
            return null
        }
    }

    override fun getRunDataContext(): ConfigurationContext {
        val context = TzDataContext()
        context.put(CommonDataKeys.PROJECT, project)
        context.put(CommonDataKeys.PSI_FILE, value.containingFile)
        context.put(LangDataKeys.MODULE, value.getModule())
        context.put(Location.DATA_KEY, PsiLocation.fromPsiElement(value.firstChild))
        return context.configutation()
    }

    override fun getRunActionText() = "Run feature..."
}