package io.nimbly.tzatziki.view.features.node

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import icons.CucumberIcons
import io.nimbly.tzatziki.util.findAllGerkinsFiles
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

interface TzatzikiNode<T : Any> {
    fun children(): List<T>
}

class ProjectNodeX(val p: Project) : AbstractTreeNode<Project>(p, p) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = p.name
        presentation.setIcon(AllIcons.General.ProjectStructure)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return findAllGerkinsFiles(p)
            .map { GherkinFileNodeX(p, it) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class GherkinFileNodeX(val p: Project, val file: GherkinFile) : AbstractTreeNode<GherkinFile>(p, file) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = file.name.substringBeforeLast(".")
        presentation.setIcon(CucumberIcons.Cucumber)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return file.features
            .map { FeatureNodeX(project, it) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class FeatureNodeX(val p: Project, val feature: GherkinFeature) : AbstractTreeNode<GherkinFeature>(p, feature) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = feature.featureName
        presentation.setIcon(AllIcons.General.ReaderMode)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return feature.scenarios
            .map { ScenarioNodeX(project, it) }
            .sortedBy { it.toString()}
            .toMutableList()
    }

}

class ScenarioNodeX(val p: Project, val scenario: GherkinStepsHolder) : AbstractTreeNode<GherkinStepsHolder>(p, scenario) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = scenario.scenarioName.trim().ifEmpty { "Scenario" }
        presentation.setIcon(AllIcons.Nodes.BookmarkGroup)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf()
    }
}




class ProjectNode(val p: Project) : NodeDescriptor<Project>(p, null), TzatzikiNode<GherkinFileNode> {
    override fun getElement() = p
    override fun update(): Boolean {
        myName = p.name
        icon = AllIcons.General.ProjectStructure
        return true
    }
    init {
        update()
    }

    override fun children(): List<GherkinFileNode> {
        return findAllGerkinsFiles(p)
            .map { GherkinFileNode(p, this, it) }
            .sortedBy { it.toString()}
    }
}

class GherkinFileNode(val p: Project, val parent: ProjectNode, val gherkinFile: GherkinFile)
        : HierarchyNodeDescriptor(p, parent, gherkinFile, false) {
    override fun update(): Boolean {
        myName = containingFile!!.name.substringBeforeLast(".")
        icon = CucumberIcons.Cucumber
        return true
    }
    init {
        update()
    }
}

class FeatureNode(val p: Project, val parent: GherkinFileNode, val feature: GherkinFeature)
        : HierarchyNodeDescriptor(p, parent, feature, false) {
    override fun update(): Boolean {
        myName = (psiElement as GherkinFeature).featureName
        icon = AllIcons.General.ReaderMode
        return true
    }
    init {
        update()
    }
}

class ScenarioNode(val p: Project, val parent: FeatureNode, val scenario: GherkinStepsHolder)
       : HierarchyNodeDescriptor(p, parent, scenario, false) {
    override fun update(): Boolean {
        myName = (psiElement as GherkinStepsHolder).scenarioName.trim().ifEmpty { "Scenario" }
        icon = AllIcons.Nodes.BookmarkGroup
        return true
    }
    init {
        update()
    }
}
