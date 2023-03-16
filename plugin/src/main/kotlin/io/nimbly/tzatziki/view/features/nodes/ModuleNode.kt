@file:Suppress("UnstableApiUsage")

package io.nimbly.tzatziki.view.features.nodes

import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.findAllGerkinsFiles
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.util.UserDataHolder

class ModuleNode(
    module: Module,
    val moduleName: String,
    val tree: ModuleGroupsTree,
    val path: List<String>,
    val exp: Expression?
) : AbstractTzNode<Module>(module.project, module, exp) { //}, TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = moduleName
        presentation.setIcon(ActionIcons.MODULE_DIRECTORY)
    }

    override fun getChildren(): List<AbstractTzNode<out UserDataHolder>> {

        val allSubModules = tree.getModulesInGroup(ModuleGroup(path))

        val subModules = mutableListOf<ModuleNode>()
        val subFiles = mutableSetOf<GherkinFileNode>()
        allSubModules.forEach { sub: Module ->
            val subName: String = ModuleGrouper.instanceFor(project).getShortenedName(sub)
            val subSubModules: Collection<Module> = tree.getModulesInGroup(ModuleGroup(path + subName))
            if (subSubModules.isNotEmpty()) {
                subModules.add(
                    ModuleNode(sub, subName, tree, path + subName, exp))
            }
            else {
                val files = findAllGerkinsFiles(sub)
                subFiles.addAll(
                    files.map { GherkinFileNode(project, it, filterByTags) }
                )
            }
        }

        return subModules.sortedBy { it.name } + subFiles.sortedBy { it.name }
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

//internal class ModuleGroupsTree private constructor(val grouper: ModuleGrouper) {
//
//    private val childGroups = MultiMap.createSet<ModuleGroup, ModuleGroup>()
//    private val childModules = MultiMap.create<ModuleGroup, Module>()
//
//    init {
//
//        val moduleAsGroupPaths = grouper.getAllModules().mapNotNullTo(HashSet()) { grouper.getModuleAsGroupPath(it) }
//        for (module in grouper.getAllModules()) {
//            val groupPath = grouper.getGroupPath(module)
//            if (groupPath.isNotEmpty()) {
//                val group = ModuleGroup(groupPath)
//                val moduleNamePrefixLen = (1 .. groupPath.size).firstOrNull { groupPath.subList(0, it) in moduleAsGroupPaths }
//                val parentGroupForModule = if (moduleNamePrefixLen != null && moduleNamePrefixLen > 1) {
//                    //if there are modules with names 'a.foo' and 'a.foo.bar.baz' the both should be shown as children of module group 'a' to avoid
//                    // nodes with same text in the tree
//                    ModuleGroup(groupPath.subList(0, moduleNamePrefixLen - 1))
//                }
//                else {
//                    group
//                }
//                childModules.putValue(parentGroupForModule, module)
//                var parentGroupPath = groupPath
//                while (parentGroupPath.size > 1 && parentGroupPath !in moduleAsGroupPaths) {
//                    val nextParentGroupPath = parentGroupPath.subList(0, parentGroupPath.size - 1)
//                    childGroups.putValue(ModuleGroup(nextParentGroupPath), ModuleGroup(parentGroupPath))
//                    parentGroupPath = nextParentGroupPath
//                }
//            }
//        }
//    }
//
//    fun getChildGroups(group: ModuleGroup): Collection<ModuleGroup> = childGroups[group]
//
//    fun getModulesInGroup(group: ModuleGroup): Collection<Module> = childModules[group]
//
//    companion object {
//        private val key = Key.create<CachedValue<ModuleGroupsTree>>("MODULE_GROUPS_TREE")
//
//        @JvmStatic
//        fun getModuleGroupTree(project: Project): ModuleGroupsTree {
//            return CachedValuesManager.getManager(project).getCachedValue(project, key, {
//                val tree = ModuleGroupsTree(ModuleGrouper.instanceFor(project))
//                CachedValueProvider.Result.createSingleDependency(tree, ProjectRootManager.getInstance(project))
//            }, false)
//        }
//    }
//}








//fun xxxgetChildren() {
//
//    val moduleDirectories: Map<Module, Path> = ProjectRootManager.getInstance(project).contentRootsFromAllModules
//        .mapNotNull { it.getDirectory(project) }
//        .map { it.getModule() to Path(it.virtualFile.path) }
//        .filterIsInstance<Pair<Module, Path>>()
//        .toMap()
//
//
//    val files = findAllGerkinsFiles(project)
//
//    val moduleDirectoriesKotlin: Map<Module, Path> = ProjectRootManager.getInstance(project).contentRootsFromAllModules
//        .mapNotNull { it.toPsiDirectory(project) }
//        .map { it.getModule()?.toModuleGroup()?.baseModule to Path(it.virtualFile.path) }
//        .filterIsInstance<Pair<Module, Path>>()
//        .toMap()
//
//    val x = moduleDirectories
//        .map { ModuleRootManager.getInstance(it.key).contentRoots.toList() }
//        .flatten()
//
//    val y = moduleDirectories
//        .map { ModuleRootManager.getInstance(it.key).modifiableModel.module }
//
//    // Que 3
//    val moduleGraph = ModuleManager.getInstance(project).moduleGraph()
//    val z = moduleGraph.nodes.map { moduleGraph.getIn(it) }
//
//    // 3 nom...
//    val zz = moduleGraph.nodes.map {
//        ModuleRootManager.getInstance(it).dependencyModuleNames.toList()
//    }.flatten()
//
//    // Ah ?
//    val zzz = files.map {
//        ModuleUtilCore.findModuleForFile(it.virtualFile, project)
//    }
//    val zzz2 = moduleGraph.nodes.map {
//        it to ModuleUtilCore.getAllDependentModules(it)
//    }
//
////
////        val grouper = ModuleGrouper.instanceFor(project).getModuleAsGroupPath(project.rootModule()!!)
////        val tree = ModuleGroupsTree.getModuleGroupTree(project)
////
////        tree.getModulesInGroup(ModuleGroup(listOf("rich-example", "supplier", "france")))
////        tree.getModulesInGroup(ModuleGroup(listOf(project.name)))
////            .map { it.name }
//
//
////        tree.getChildGroups(grouper)
//
//
//    val moduleDir = moduleDirectories[value]
//
//    val modulePaths: List<Pair<Path, Module>> = moduleDirectories
//        .map { it.value to it.key }
//        .sortedByDescending { it.first }
//
//    val fileModules: Map<GherkinFile, Module> = files
//        .map { file ->
//            val p = Path(file.virtualFile.path)
//            file to modulePaths.find { p.startsWith(it.first) }
//        }
//        .map { it.first to it.second?.second }
//        .filterIsInstance<Pair<GherkinFile, Module>>()
//        .toMap()
//
//    val moduleFiles: List<GherkinFileNode> = fileModules
//        .filter { it.value == value }
//        .map { GherkinFileNode(project, it.key, filterByTags) }
//        .sortedBy { it.toString() }
//
////        val subModules = moduleDirectories.keys
////            .filter {
////                val p = moduleDirectories[it]
////                p?.parent == moduleDir
////            }
////            .map { ModuleNode(it, filterByTags) }
////            .sortedBy { it.toString() }
//
////        return moduleFiles + subModules
//}
