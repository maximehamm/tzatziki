package io.nimbly.tzatziki.view.features

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
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import icons.ActionIcons
import icons.CucumberIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaAllFeaturesInFolderRunConfigurationProducer
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaFeatureRunConfigurationProducer
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaScenarioRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

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
}

class GherkinTagNode(p: Project, tag: String, val gherkinFiles: List<GherkinFile>, val filterByTags: Expression?) : AbstractTreeNode<String>(p, tag) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value
        presentation.setIcon(ActionIcons.TAG)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return gherkinFiles
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinFileNode(project, it, filterByTags) }
            .toMutableList()
    }
}

class GherkinFileNode(p: Project, file: GherkinFile, exp: Expression?) : AbstractTzPsiElementNode<GherkinFile>(p, file, exp) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name.substringBeforeLast(".")
        presentation.setIcon(CucumberIcons.Cucumber)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return value.features
            .filter { it.checkExpression(filterByTags) }
            .map { FeatureNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class FeatureNode(p: Project, feature: GherkinFeature, exp: Expression?) : AbstractTzPsiElementNode<GherkinFeature>(p, feature, exp), TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.featureName
        presentation.setIcon(AllIcons.General.ReaderMode)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return value.scenarios
            .filter { it.scenarioKeyword != "Background" }
            .filter { it.checkExpression(filterByTags) }
            .map { ScenarioNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }

    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
        val runConfProds = RunConfigurationProducer.getProducers(project)
        return runConfProds.find { it.javaClass == CucumberJavaFeatureRunConfigurationProducer::class.java }
    }

    override fun getRunDataContext(): ConfigurationContext {
        val dataContext = TzDataContext()
        dataContext.put(CommonDataKeys.PROJECT, project)
        dataContext.put(CommonDataKeys.PSI_FILE, value.containingFile)
        dataContext.put(LangDataKeys.MODULE, value.getModule())
        dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(value.firstChild))
        return ConfigurationContext.getFromContext(dataContext)
    }
}

class ScenarioNode(p: Project, scenario: GherkinStepsHolder, exp: Expression?) : AbstractTzPsiElementNode<GherkinStepsHolder>(p, scenario, exp), TzRunnableNode {

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
        val dataContext = TzDataContext()
        dataContext.put(CommonDataKeys.PROJECT, project)
        dataContext.put(CommonDataKeys.PSI_FILE, value.containingFile)
        dataContext.put(CucumberPlusDataKeys.MODULE, value.getModule())
        dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(value.firstChild))
        return ConfigurationContext.getFromContext(dataContext)
    }
}

interface TzRunnableNode {

    fun getRunConfiguration(): RunConfigurationProducer<*>?
    fun getRunDataContext(): ConfigurationContext
}

abstract class AbstractTzPsiElementNode<T: PsiElement>(
    p: Project,
    value: T,
    filterByTags: Expression?
) : AbstractTzNode<T>(p, value, filterByTags)

abstract class AbstractTzNode<T: Any>(
    p: Project,
    value: T,
    var filterByTags: Expression?
) : AbstractTreeNode<T>(p, value), Navigatable


