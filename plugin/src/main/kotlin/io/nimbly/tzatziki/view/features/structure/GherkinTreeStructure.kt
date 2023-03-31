package io.nimbly.tzatziki.view.features.structure

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.findModuleForFile
import io.nimbly.tzatziki.util.parent
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.GherkinFeatureNode
import io.nimbly.tzatziki.view.features.nodes.GherkinFileNode
import io.nimbly.tzatziki.view.features.nodes.ModuleNode
import io.nimbly.tzatziki.view.features.nodes.createModuleNode
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor

/**
 * Some documentation :
 * https://intellij-sdk-docs-cn.github.io/intellij/sdk/docs/user_interface_components/lists_and_trees.html?search=tree
 */
abstract class GherkinTreeStructure(private val panel: FeaturePanel) : AbstractTreeStructure() {

    var filterByTags: Expression? = null
        set(value) {
            field = value
            root = createModuleNode(panel.project, filterByTags)
        }

    private var root: ModuleNode
        = createModuleNode(panel.project, filterByTags)

    override fun commit() = Unit
    override fun hasSomethingToCommit() = false

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?)
            = element as NodeDescriptor<*>

    override fun getRootElement(): Any = root

    // PROJECT < ... < FILE < FEATURE < SCENARIO
    override fun getParentElement(element: Any): Any? {
        val project = panel.project
        when (element) {
            is ModuleNode -> {
                val parentModule = element.value?.parent
                    ?: return null
                return createModuleNode(project, filterByTags, parentModule)
            }

            is GherkinFile -> {
                val mainModule = project.findModuleForFile(element)
                return createModuleNode(project, filterByTags, mainModule)
            }

            is GherkinFeature -> {
                return GherkinFileNode(element.project, element.parent as GherkinFile, filterByTags)
            }

            is GherkinStepsHolder -> {
                return GherkinFeatureNode(element.project, element.parent as GherkinFeature, filterByTags)
            }

            else -> return null
        }
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()
        return emptyArray<Any>()
    }
}