package io.nimbly.tzatziki.view.features

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
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

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
    override fun navigate(requestFocus: Boolean) {
//        val v = value
//        if (v is PsiElement) {
//
//            val fileEditorManager = FileEditorManager.getInstance(project)
//            val virtualFile = v.containingFile.virtualFile
//            fileEditorManager.openFile(virtualFile, true, fileEditorManager.isFileOpen(virtualFile))
//            fileEditorManager.setSelectedEditor(virtualFile, "text-editor")
//        }

    }
}
