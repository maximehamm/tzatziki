@file:Suppress("UnstableApiUsage")

package io.nimbly.tzatziki.util

import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import java.nio.file.Path

val Module.parent: Module?
    get() {
        return this.project.modulesTree()
            ?.find(this)
            ?.parent
            ?.value
    }

val Module.subModules: List<Module>
    get() {
        return this.project.modulesTree()
            ?.find(this)
            ?.children
            ?.map { it.value }
            ?: emptyList()
    }

val Module.simpleName: String
    get() {
        val scope = this.moduleContentScope as? ModuleWithDependenciesScope
            ?: return this.name

        return scope.roots.firstOrNull()?.presentableName
            ?: return this.name
    }

fun Project.modulesTree(): Node<Module>? {

    val moduleMap = mutableMapOf<Path, Module>()
    ModuleManager.getInstance(this).sortedModules.forEach { m ->
        val scope = m.moduleContentScope as? ModuleWithDependenciesScope
        scope?.roots?.forEach {
            moduleMap[it.path.toPath()] = m
        }
    }

    return moduleMap.toTree()
}

fun Project.getModuleManager(): ModuleManager
        = getService(ModuleManager::class.java)


fun createModuleGroupTree(project: Project): ModuleGroupsTree {
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
    fun root(): Module? = grouper.getAllModules().firstOrNull()
}

private val key = Key.create<CachedValue<ModuleGroupsTree>>("TZ_MODULE_GROUPS_TREE")




