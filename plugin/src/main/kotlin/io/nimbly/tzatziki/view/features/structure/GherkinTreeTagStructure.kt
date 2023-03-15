package io.nimbly.tzatziki.view.features.structure

import com.intellij.ide.util.treeView.AbstractTreeNode
import io.nimbly.tzatziki.util.getModule
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.*
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import java.util.SortedMap

class GherkinTreeTagStructure(panel: FeaturePanel) : GherkinTreeStructure(panel) {

    var tags: SortedMap<String, List<GherkinFile>>? = null

    var groupTag: Boolean = false

    // PROJECT < ... < (TAG) < FILE < FEATURE < SCENARIO
    override fun getParentElement(element: Any): Any? {
        if (!groupTag)
            return super.getParentElement(element)

        if (element is GherkinFile) {
            return ModuleNode(element.getModule()!!, filterByTags)
        }

        return super.getParentElement(element)
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (!groupTag)
            return super.getChildElements(element)

        if (element is ModuleNode)
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
