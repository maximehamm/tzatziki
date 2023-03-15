package io.nimbly.tzatziki.view.features.nodes

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
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaAllFeaturesInFolderRunConfigurationProducer

class ProjectNode(p: Project, exp: Expression?) : AbstractTzNode<Project>(p, p, exp), TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.setIcon(AllIcons.General.ProjectStructure)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return findAllGerkinsFiles(project)
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinFileNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }

    override fun isAlwaysExpand() = true

    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
        val runConfProds = RunConfigurationProducer.getProducers(project)
        return runConfProds.find { it.javaClass == CucumberJavaAllFeaturesInFolderRunConfigurationProducer::class.java }
    }

    override fun getRunDataContext(): ConfigurationContext {
        val dataContext = TzDataContext()
        dataContext.put(CommonDataKeys.PROJECT, project)

        val basePath = value.basePath
        if (basePath != null) {

            val psiDirectory = project.getDirectory()
            if (psiDirectory != null) {

                dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiDirectory))
                dataContext.put(LangDataKeys.MODULE, psiDirectory.getModule())
            }
        }

        return ConfigurationContext.getFromContext(dataContext)
    }

    override fun getRunActionText() = "Run all Cucumber tests..."
}