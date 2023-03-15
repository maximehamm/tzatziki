package io.nimbly.tzatziki.view.features.structure

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.components.ComponentManager
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.getModule
import io.nimbly.tzatziki.util.rootModule
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.*
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

abstract class GherkinTreeStructure(private val panel: FeaturePanel) : AbstractTreeStructure() {

    var filterByTags: Expression? = null
        set(value) {
            field = value
            root = rootNode(panel.project, filterByTags)
        }

    private var root: AbstractTzNode<out ComponentManager>
        = rootNode(panel.project, filterByTags)

    override fun commit() = Unit
    override fun hasSomethingToCommit() = false

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?)
            = element as NodeDescriptor<*>

    override fun getRootElement(): Any = root

    // PROJECT < ... < FILE < FEATURE < SCENARIO
    override fun getParentElement(element: Any): Any? {
        if (element is GherkinFile) {
            return ModuleNode(element.getModule()!!, filterByTags)
        } else if (element is GherkinFeature) {
            return GherkinFileNode(element.project, element.parent as GherkinFile, filterByTags)
        } else if (element is GherkinStepsHolder) {
            return GherkinFeatureNode(element.project, element.parent as GherkinFeature, filterByTags)
        }
        return null
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()
        return emptyArray<Any>()
    }
}