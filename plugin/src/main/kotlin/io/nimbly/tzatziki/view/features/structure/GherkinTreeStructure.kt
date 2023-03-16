package io.nimbly.tzatziki.view.features.structure

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.getModule
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.AbstractTzNode
import io.nimbly.tzatziki.view.features.nodes.GherkinFeatureNode
import io.nimbly.tzatziki.view.features.nodes.GherkinFileNode
import io.nimbly.tzatziki.view.features.nodes.ModuleNode
import io.nimbly.tzatziki.view.features.nodes.createModuleNode
import io.nimbly.tzatziki.view.features.nodes.parent
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.components.ComponentManager

@Suppress("UnstableApiUsage")
abstract class GherkinTreeStructure(private val panel: FeaturePanel) : AbstractTreeStructure() {

    var filterByTags: Expression? = null
        set(value) {
            field = value
            root = createModuleNode(panel.project, filterByTags)
        }

    private var root: AbstractTzNode<out ComponentManager>
        = createModuleNode(panel.project, filterByTags)

    override fun commit() = Unit
    override fun hasSomethingToCommit() = false

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?)
            = element as NodeDescriptor<*>

    override fun getRootElement(): Any = root

    // PROJECT < ... < FILE < FEATURE < SCENARIO
    override fun getParentElement(element: Any): Any? {
        if (element is ModuleNode) {
            return createModuleNode(panel.project, filterByTags, element.value!!.parent())
        } else if (element is GherkinFile) {
            return createModuleNode(panel.project, filterByTags, element.getModule()!!)
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