@file:Suppress("UnstableApiUsage")

package io.nimbly.tzatziki.view.features.nodes

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.rootModule
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap

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

    val path = ModuleGrouper.instanceFor(this.project).getGroupPath(this).dropLast(1)
    val parent: Module? = tree.getModulesInGroup(ModuleGroup(path)).firstOrNull()

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

//val <T : PsiElement> AbstractTzSmartPsiElementNode<T>.valueElement
//    get() = value
//        .element

internal fun createModuleGroupTree(project: Project): ModuleGroupsTree {
    return CachedValuesManager.getManager(project).getCachedValue(project, key, {
        val tree = ModuleGroupsTree(ModuleGrouper.instanceFor(project))
        CachedValueProvider.Result.createSingleDependency(tree, ProjectRootManager.getInstance(project))
    }, false)
}

class ModuleGroupsTree (val grouper: ModuleGrouper) {

    private val childModules = MultiMap.create<ModuleGroup, Module>()

    init {

        val moduleAsGroupPaths = grouper.getAllModules().mapNotNullTo(HashSet()) { grouper.getModuleAsGroupPath(it) }
        for (module in grouper.getAllModules()) {
            val groupPath = grouper.getGroupPath(module)
            if (groupPath.isNotEmpty()) {
                val group = ModuleGroup(groupPath)
                val moduleNamePrefixLen = (1 .. groupPath.size).firstOrNull { groupPath.subList(0, it) in moduleAsGroupPaths }
                val parentGroupForModule: ModuleGroup = if (moduleNamePrefixLen != null && moduleNamePrefixLen > 1) {
                    //if there are modules with names 'a.foo' and 'a.foo.bar.baz' the both should be shown as children of module group 'a' to avoid
                    // nodes with same text in the tree
                    ModuleGroup(groupPath.subList(0, moduleNamePrefixLen - 1))
                }
                else {
                    group
                }
                childModules.putValue(parentGroupForModule, module)

            }
        }
    }

    fun getModulesInGroup(group: ModuleGroup): Collection<Module> = childModules[group]
}

private val key = Key.create<CachedValue<ModuleGroupsTree>>("TZ_MODULE_GROUPS_TREE")

