package io.nimbly.tzatziki.view.features.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolder
import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.util.findAllGerkinsFiles
import io.nimbly.tzatziki.util.getModule
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.nio.file.Path
import kotlin.io.path.Path

class ModuleNode(m: Module, exp: Expression?) : AbstractTzNode<Module>(m.project, m, exp) { //}, TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.toModuleGroup().baseModule.name.substringAfterLast(".")
        presentation.setIcon(ActionIcons.MODULE_DIRECTORY)
    }

    override fun getChildren(): List<AbstractTzNode<out UserDataHolder>> {

        val files = findAllGerkinsFiles(project)

        val moduleDirectories: Map<Module, Path> = ProjectRootManager.getInstance(project).contentRootsFromAllModules
            .mapNotNull { it.toPsiDirectory(project) }
            .map { it.getModule()?.toModuleGroup()?.baseModule to Path(it.virtualFile.path) }
            .filterIsInstance<Pair<Module, Path>>()
            .toMap()

        val moduleDir = moduleDirectories[value]

        val modulePaths: List<Pair<Path, Module>> = moduleDirectories
            .map { it.value to it.key }
            .sortedByDescending { it.first }

        val fileModules: Map<GherkinFile, Module> = files
            .map { file ->
                val p = Path(file.virtualFile.path)
                file to modulePaths.find { p.startsWith(it.first) }
            }
            .map { it.first to it.second?.second }
            .filterIsInstance<Pair<GherkinFile, Module>>()
            .toMap()

        val moduleFiles: List<GherkinFileNode> = fileModules
            .filter { it.value == value }
            .map { GherkinFileNode(project, it.key, filterByTags) }
            .sortedBy { it.toString() }

        val subModules = moduleDirectories.keys
            .filter {
                val p = moduleDirectories[it]
                p?.parent == moduleDir
            }
            .map { ModuleNode(it, filterByTags) }
            .sortedBy { it.toString() }

        return moduleFiles + subModules
    }

    override fun isAlwaysExpand() = true

//    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
//        val runConfProds = RunConfigurationProducer.getProducers(project)
//        return runConfProds.find { it.javaClass == CucumberJavaAllFeaturesInFolderRunConfigurationProducer::class.java }
//    }
//
//    override fun getRunDataContext(): ConfigurationContext {
//        val dataContext = TzDataContext()
//        dataContext.put(CommonDataKeys.PROJECT, project)
//
//        val basePath = value.basePath
//        if (basePath != null) {
//
//            val psiDirectory = project.getDirectory()
//            if (psiDirectory != null) {
//
//                dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiDirectory))
//                dataContext.put(LangDataKeys.MODULE, psiDirectory.getModule())
//            }
//        }
//
//        return ConfigurationContext.getFromContext(dataContext)
//    }
//
//    override fun getRunActionText() = "Run all Cucumber tests..."
}