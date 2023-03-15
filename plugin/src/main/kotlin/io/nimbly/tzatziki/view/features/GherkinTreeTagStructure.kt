package io.nimbly.tzatziki.view.features

import com.intellij.ide.util.treeView.AbstractTreeNode
import io.nimbly.tzatziki.view.features.nodes.GherkinFeatureNode
import io.nimbly.tzatziki.view.features.nodes.GherkinFileNode
import io.nimbly.tzatziki.view.features.nodes.GherkinTagNode
import io.nimbly.tzatziki.view.features.nodes.ProjectNode
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
            return ProjectNode(element.project, filterByTags)
        } else if (element is GherkinFeature) {
            return GherkinFileNode(element.project, element.parent as GherkinFile, filterByTags)
        } else if (element is GherkinStepsHolder) {
            return GherkinFeatureNode(element.project, element.parent as GherkinFeature, filterByTags)
        }
        return null
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (!groupByTags)
            return super.getChildElements(element)

        if (element is ProjectNode)
            return tags
                ?.filter { filterByTags?.evaluate(listOf("@" + it.key)) ?: true }
                ?.map { GherkinTagNode(
                    element.project,
                    it.key,
                    it.value.sortedBy { it.name },
                    filterByTags
                ) }?.toTypedArray()
                ?: emptyArray()

        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()

        return emptyArray()
    }
}