package io.nimbly.tzatziki.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.nio.file.Path

val Module.parent: Module?
    get() {
        return this.project.modulesTree()
            ?.findByValue(this)
            ?.parent
            ?.value
    }

val Module.subModules: List<Module>
    get() {
        return this.project.modulesTree()
            ?.findByValue(this)
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

val Module.path: Path
    get() {
        val scope = this.moduleContentScope as? ModuleWithDependenciesScope
            ?: return this.moduleNioFile

        return scope.roots.firstOrNull()?.path?.toPath()
            ?: return this.moduleNioFile
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

fun Project.findModuleForFile(file: PsiFile) : Module? {

    var path = file.virtualFile.path.toPath()

    val moduleMap = mutableMapOf<Path, Module>()
    ModuleManager.getInstance(this).sortedModules.forEach { m ->
        val scope = m.moduleContentScope as? ModuleWithDependenciesScope
        scope?.roots?.forEach {
            moduleMap[it.path.toPath()] = m
        }
    }

    while (path.nameCount > 0) {
        val module = moduleMap.get(path)
        if (module != null)
            return module
        path = path.parent
    }

    return null
}



