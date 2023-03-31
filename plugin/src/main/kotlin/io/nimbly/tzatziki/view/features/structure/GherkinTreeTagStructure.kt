package io.nimbly.tzatziki.view.features.structure

import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.GherkinTagNode
import io.nimbly.tzatziki.view.features.nodes.ModuleNode
import io.nimbly.tzatziki.view.features.nodes.createModuleNode
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.util.*

class GherkinTreeTagStructure(panel: FeaturePanel) : GherkinTreeStructure(panel) {

    var tags: SortedMap<String, List<GherkinFile>>? = null

    var groupTag: Boolean = false

    // PROJECT < ... < (TAG) < FILE < FEATURE < SCENARIO
    override fun getParentElement(element: Any): Any? {
        if (!groupTag)
            return super.getParentElement(element)

        if (element is GherkinTagNode) {
            return createModuleNode(element.project, filterByTags)
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

        return super.getChildElements(element)
    }
}
