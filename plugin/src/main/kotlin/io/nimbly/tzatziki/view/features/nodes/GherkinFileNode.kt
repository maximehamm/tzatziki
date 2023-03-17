package io.nimbly.tzatziki.view.features.nodes

import icons.CucumberIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.checkExpression
import io.nimbly.tzatziki.util.emptyConfigurationContext
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaFeatureRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

class GherkinFileNode(p: Project, val file: GherkinFile, exp: Expression?) : AbstractTzPsiElementNode<GherkinFile>(p, file, exp), TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name.substringBeforeLast(".")
        presentation.setIcon(CucumberIcons.Cucumber)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return value.features
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinFeatureNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }

    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
        val runConfProds = RunConfigurationProducer.getProducers(project)
        return runConfProds.find { it.javaClass == CucumberJavaFeatureRunConfigurationProducer::class.java }
    }

    override fun getRunActionText() = "Run ${value.name.substringBeforeLast(".")}..."

    override fun getRunDataContext(): ConfigurationContext {

        return (children.firstOrNull() as? TzRunnableNode)?.getRunDataContext()
            ?: emptyConfigurationContext()
    }
}