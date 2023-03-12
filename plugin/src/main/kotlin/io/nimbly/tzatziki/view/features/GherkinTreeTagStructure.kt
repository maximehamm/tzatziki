package io.nimbly.tzatziki.view.features

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import java.util.SortedMap

class GherkinTreeTagStructure(panel: FeaturePanel) : GherkinTreeStructure(panel) {

    var tags: SortedMap<String, List<GherkinFile>>? = null

    var groupByTags: Boolean = false

    override fun getParentElement(element: Any): Any? {
        if (!groupByTags)
            return super.getParentElement(element)

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
        if (!groupByTags)
            return super.getChildElements(element)

        if (element is ProjectNode)
            return tags
                ?.map { GherkinTagNode(element.project, it.key, it.value.sortedBy { it.name }) }?.toTypedArray()
                ?: emptyArray()

        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()

        return emptyArray()
    }
}