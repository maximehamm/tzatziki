package io.nimbly.tzatziki.view.features.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

class EmptyNode(project: Project) : AbstractTreeNode<Project>(project, project) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = "No module found"
        presentation.setIcon(AllIcons.General.Information)
    }

    override fun getChildren(): List<AbstractTreeNode<*>> = emptyList()
}
