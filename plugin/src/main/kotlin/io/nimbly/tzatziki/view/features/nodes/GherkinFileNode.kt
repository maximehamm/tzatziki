package io.nimbly.tzatziki.view.features.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import icons.CucumberIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.checkExpression
import org.jetbrains.plugins.cucumber.psi.GherkinFile

class GherkinFileNode(p: Project, file: GherkinFile, exp: Expression?) : AbstractTzPsiElementNode<GherkinFile>(p, file, exp) {

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
}