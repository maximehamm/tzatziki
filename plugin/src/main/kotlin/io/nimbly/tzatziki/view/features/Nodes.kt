package io.nimbly.tzatziki.view.features

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import icons.ActionIcons
import icons.CollaborationToolsIcons
import icons.CucumberIcons
import io.nimbly.tzatziki.util.findAllGerkinsFiles
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

class ProjectNode(val p: Project) : AbstractNode<Project>(p, p) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = p.name
        presentation.setIcon(AllIcons.General.ProjectStructure)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return findAllGerkinsFiles(p)
            .map { GherkinFileNode(p, it) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class GherkinTagNode(val p: Project, val tag: String, val gherkinFiles: List<GherkinFile>) : AbstractTreeNode<String>(p, tag) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = tag
        presentation.setIcon(ActionIcons.TAG)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return gherkinFiles
            .map { GherkinFileNode(p, it) }
            .toMutableList()
    }
}

class GherkinFileNode(p: Project, val file: GherkinFile) : AbstractNode<GherkinFile>(p, file) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = file.name.substringBeforeLast(".")
        presentation.setIcon(CucumberIcons.Cucumber)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return file.features
            .map { FeatureNode(project, it) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class FeatureNode(p: Project, val feature: GherkinFeature) : AbstractNode<GherkinFeature>(p, feature) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = feature.featureName
        presentation.setIcon(AllIcons.General.ReaderMode)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return feature.scenarios
            .map { ScenarioNode(project, it) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class ScenarioNode(p: Project, val scenario: GherkinStepsHolder) : AbstractNode<GherkinStepsHolder>(p, scenario) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = scenario.scenarioName.trim().ifEmpty { "Scenario" }
        presentation.setIcon(AllIcons.Nodes.BookmarkGroup)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf()
    }
}


abstract class AbstractNode<T: Any> (p: Project, value: T) : AbstractTreeNode<T>(p, value), Navigatable {

}
