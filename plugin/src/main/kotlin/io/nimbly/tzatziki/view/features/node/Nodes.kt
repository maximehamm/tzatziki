package io.nimbly.tzatziki.view.features.node

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import icons.CucumberIcons
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

class ProjectNode(val p: Project) : NodeDescriptor<Project>(p, null) {
    override fun getElement() = p
    override fun update(): Boolean {
        myName = p.name
        icon = AllIcons.General.ProjectStructure
        return true
    }
    init {
        update()
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
