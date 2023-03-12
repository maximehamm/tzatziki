package io.nimbly.tzatziki.view.features

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.pom.Navigatable
import icons.ActionIcons
import icons.CucumberIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.checkExpression
import io.nimbly.tzatziki.util.findAllGerkinsFiles
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import java.util.*

class ProjectNode(val p: Project, exp: Expression?) : AbstractNode<Project>(p, p, exp) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = p.name
        presentation.setIcon(AllIcons.General.ProjectStructure)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return findAllGerkinsFiles(p)
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinFileNode(p, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }

    override fun isAlwaysExpand(): Boolean {
        return true
    }

//    private val comparingValue get() = value.toString() + "#" + filterByTags?.toString()
//
//    override fun hashCode(): Int {
//        return super.hashCode() + filterByTags.toString().hashCode()
//    }
//    override fun equals(other: Any?): Boolean {
//        if (other === this) return true
//        if (other == null || other.javaClass != javaClass)
//            return false
//        return Comparing.equal<Any>(
//            comparingValue,
//            (other as ProjectNode).comparingValue
//        )
//    }
}

class GherkinTagNode(val p: Project, val tag: String, val gherkinFiles: List<GherkinFile>, val filterByTags: Expression?) : AbstractTreeNode<String>(p, tag) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = tag
        presentation.setIcon(ActionIcons.TAG)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return gherkinFiles
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinFileNode(p, it, filterByTags) }
            .toMutableList()
    }
}

class GherkinFileNode(p: Project, val file: GherkinFile, exp: Expression?) : AbstractNode<GherkinFile>(p, file, exp) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = file.name.substringBeforeLast(".")
        presentation.setIcon(CucumberIcons.Cucumber)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return file.features
            .filter { it.checkExpression(filterByTags) }
            .map { FeatureNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class FeatureNode(p: Project, val feature: GherkinFeature, exp: Expression?) : AbstractNode<GherkinFeature>(p, feature, exp) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = feature.featureName
        presentation.setIcon(AllIcons.General.ReaderMode)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return feature.scenarios
            .filter { it.checkExpression(filterByTags) }
            .map { ScenarioNode(project, it, filterByTags) }
            .sortedBy { it.toString()}
            .toMutableList()
    }
}

class ScenarioNode(p: Project, val scenario: GherkinStepsHolder, exp: Expression?) : AbstractNode<GherkinStepsHolder>(p, scenario, exp) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = scenario.scenarioName.trim().ifEmpty { "Scenario" }
        presentation.setIcon(AllIcons.Nodes.BookmarkGroup)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf()
    }
}


abstract class AbstractNode<T: Any>(
    p: Project,
    value: T,
    var filterByTags: Expression?
) : AbstractTreeNode<T>(p, value), Navigatable {

//    private val comparingValue get() = value.toString() + "#" + filterByTags?.toString()
//
//    override fun hashCode(): Int {
//        return super.hashCode() + filterByTags.toString().hashCode()
//    }
//    override fun equals(other: Any?): Boolean {
//        if (other === this) return true
//        if (other == null || other.javaClass != javaClass)
//            return false
//        return Comparing.equal<Any>(
//            comparingValue,
//            (other as AbstractNode<*>).comparingValue
//        )
//    }
}
