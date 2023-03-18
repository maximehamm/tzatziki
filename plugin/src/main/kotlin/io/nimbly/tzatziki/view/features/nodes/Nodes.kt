@file:Suppress("UnstableApiUsage")

package io.nimbly.tzatziki.view.features.nodes

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.createModuleGroupTree
import io.nimbly.tzatziki.util.rootModule
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

interface TzRunnableNode {
    fun getRunConfiguration(): RunConfigurationProducer<*>?
    fun getRunDataContext(): ConfigurationContext
    fun getRunActionText(): String
}

interface TzPrintable {
}

abstract class AbstractTzPsiElementNode<T: PsiElement>(
    p: Project,
    value: T,
    filterByTags: Expression?
) : AbstractTzNode<T>(p, value, filterByTags)

abstract class AbstractTzNode<T: Any>(
    p: Project,
    value: T,
    var filterByTags: Expression?
) : AbstractTreeNode<T>(p, value), Navigatable

fun Module.parent(): Module? {

    val tree = createModuleGroupTree(this.project)

    val fullPath = ModuleGrouper.instanceFor(this.project).getGroupPath(this)
    val path = fullPath.dropLast(1)
    val parent: Module?
    if (path.isEmpty()) {
        val p = tree.root()
        parent = if (p == this) null else p
    } else {
        val modulesInGroup = tree.getModulesInGroup(ModuleGroup(path))
        parent = modulesInGroup.find { this.name.startsWith(it.name)}
    }

   return parent
}

fun createModuleNode(
    project: Project,
    filterByTags: Expression?,
    fromModule: Module? = null
): ModuleNode {

    val tree = createModuleGroupTree(project)

    val module: Module
    val path: List<String>
    if (fromModule == null) {
        module = project.rootModule()!!
        path = listOf(project.name)
    }
    else {
        module = fromModule
        path = ModuleGrouper.instanceFor(project).getGroupPath(module)
    }

    val name = path.last()
    return ModuleNode(module, name, tree, path, filterByTags)
}
