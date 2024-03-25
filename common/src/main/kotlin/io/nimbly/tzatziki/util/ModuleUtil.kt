package io.nimbly.tzatziki.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
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
            ?: return this.project.basePath?.toPath() ?: "".toPath()

        return scope.roots.firstOrNull()?.path?.toPath()
            ?: return this.project.basePath?.toPath() ?: "".toPath()
    }

fun VirtualFile.findProject(): Project? {
    return ProjectManager.getInstance().openProjects
        .filter { !it.isDisposed }
        .firstOrNull() { this.getFile(it) != null }
}
fun Project.modulesTree(): Node<Module>? {

    val moduleMap = mutableMapOf<Path, Module>()
    this.moduleManager.sortedModules.forEach { m ->
        val scope = m.moduleContentScope as? ModuleWithDependenciesScope
        scope?.roots?.forEach {
            moduleMap[it.path.toPath()] = m
        }
    }

    return moduleMap.toTree()
}

val Project.moduleManager: ModuleManager
    get() = getService(ModuleManager::class.java)

fun Project.findModuleForFile(file: PsiFile) : Module? {

    var path = file.virtualFile.path.toPath()

    val moduleMap = mutableMapOf<Path, Module>()
    this.moduleManager.sortedModules.forEach { m ->
        val scope = m.moduleContentScope as? ModuleWithDependenciesScope
        scope?.roots?.forEach {
            moduleMap[it.path.toPath()] = m
        }
    }

    while (path.nameCount > 0) {
        val module = moduleMap[path]
        if (module != null)
            return module
        path = path.parent
    }

    return null
}

data class ModuleId(val name: String) {

    val presentableName: String
        get() = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModuleId) return false
        return name == other.name
    }

    override fun hashCode(): Int  = name.hashCode()
}



