package io.nimbly.tzatziki.view.features

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

abstract class GherkinTreeStructure(panel: FeaturePanel) : AbstractTreeStructure() {

    private val root = ProjectNode(panel.project)

    override fun commit() = Unit
    override fun hasSomethingToCommit() = false

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?)
            = element as NodeDescriptor<*>

    override fun getRootElement(): Any = root

    override fun getParentElement(element: Any): Any? {
        if (element is GherkinFile) {
            return ProjectNode(element.project)
        } else if (element is GherkinFeature) {
            return GherkinFileNode(element.project, element.parent as GherkinFile)
        } else if (element is GherkinStepsHolder) {
            return FeatureNode(element.project, element.parent as GherkinFeature)
        }
        return null
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()
        return emptyArray<Any>()
    }
}