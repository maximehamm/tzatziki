package io.nimbly.tzatziki.view.features.node

import com.intellij.icons.AllIcons
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

class GherkinFileNode(val p: Project, val gherkinFile: GherkinFile): NodeDescriptor<GherkinFile>(p, null) {
    override fun getElement() = gherkinFile
    override fun update(): Boolean {
        myName = element.containingFile.name.substringBeforeLast(".")
        icon = CucumberIcons.Cucumber
        return true
    }
    init {
        update()
    }
}

class FeatureNode(val p: Project, val feature: GherkinFeature): NodeDescriptor<GherkinFeature>(p, null) {
    override fun getElement() = feature
    override fun update(): Boolean {
        myName = element.featureName
        icon = AllIcons.General.ReaderMode
        return true
    }
    init {
        update()
    }
}

class ScenarioNode(val p: Project, val scenario: GherkinStepsHolder): NodeDescriptor<GherkinStepsHolder>(p, null) {
    override fun getElement() = scenario
    override fun update(): Boolean {
        myName = element.scenarioName.trim().ifEmpty { "Scenario" }
        icon = AllIcons.Nodes.BookmarkGroup
        return true
    }
    init {
        update()
    }
}
