package io.nimbly.tzatziki.view.features.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.checkExpression
import org.jetbrains.plugins.cucumber.psi.GherkinFile

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