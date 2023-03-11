package io.nimbly.tzatziki.view.features.example.nodetype

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import io.nimbly.tzatziki.view.features.example.GherkinTagsToolWindowSettings
import io.nimbly.tzatziki.view.features.example.LayoutType
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Represents the root element of the tree in the Gherkin Tags tool window.
 */
class ModelDataRoot(project: Project) : AbstractNodeType("Tags rootx", project), CategoriesHolder {

    override var categories: MutableList<Category> = mutableListOf()
        private set

    /**
     * Stores model data grouped by various content roots. These roots may be Modules, Content Roots, external sources
     * like .jar files, etc.
     */
    private var contentRoots: MutableList<ContentRoot> = mutableListOf()

    init {
        initData()
    }

    /**
     * Updates the display name of the tree's root element to reflect the contents of the project in terms of
     * the types of BDD files it contains.
     */
    fun updateDisplayName() {
//        ProjectBDDTypeService service = project.getService(ProjectBDDTypeService.class);
//        if (service.hasOnlyJBehaveStoryFiles()) {
//            displayName = GherkinBundle.toolWindow("root.name.metas");
//        } else if (service.hasBothGherkinAndStoryFiles()) {
//            displayName = GherkinBundle.toolWindow("root.name.tags.and.metas");
//        } else {
//            displayName = GherkinBundle.toolWindow("root.name.tags");
//        }
    }

    /**
     * Initializes the proper model data based on the currently selected layout in the tool window.
     *
     *
     * The model data is initialized only when it hasn't been initialized.
     */
    fun initData() {
        if (GherkinTagsToolWindowSettings.getInstance(project).layout == LayoutType.NO_GROUPING) {
            if (!isProjectDataInitialized) {
                categories = SmartList<Category>(Category.createOther(project))
            }
        } else if (!isContentRootDataInitialized) {
            contentRoots = SmartList()
        }
    }

    val isProjectDataInitialized: Boolean
        get() = categories != null

    val isContentRootDataInitialized: Boolean
        get() = contentRoots != null

    fun getContentRoots(): List<ContentRoot?>? {
        return contentRoots
    }

    /**
     * Adds the argument content root to this node.
     */
    fun add(contentRoot: ContentRoot): ModelDataRoot {
        contentRoots!!.add(contentRoot)
        return this
    }

    val contentRootsByLayout: List<ContentRoot?>
        /**
         * Returns the list of content roots filtered by the content root type corresponding to the current layout selected
         * in the tool window.
         */
        get() = contentRoots!!.stream()
            .filter { contentRoot: ContentRoot? -> if (GherkinTagsToolWindowSettings.getInstance(project).layout == LayoutType.GROUP_BY_MODULES) contentRoot!!.isModule else contentRoot!!.isContentRoot }
            .collect(Collectors.toList())

    /**
     * Finds the [ContentRoot] the argument file is contained by.
     *
     *
     * If the file is not linked to any content root yet, then based on whether it actually belongs to a project content root,
     * it is added to a new `ContentRoot`, or to a catch-all content root called `Rootless`.
     *
     *
     * If the provided file is not valid anymore, it means it has just been deleted, thus the logic is slightly
     * different to locate the ContentRoot it was linked to.
     *
     * @param bddFile the file to find the content root of
     * @return the content root the file is/was linked to, or the catch-all content root
     */
    fun findContentRootOrRootless(bddFile: PsiFile): ContentRoot? {
        val contentRootForFile = ModuleUtilCore.findModuleForFile(bddFile)
        if (bddFile.virtualFile.isValid) {
            return if (contentRootForFile == null) getContentRoot(
                rootless,
                ROOTLESS_CONTENT_ROOT_NAME
            ) //if file doesn't belong to any content root
            else getContentRoot(
                getContentRoot(contentRootForFile.name),
                contentRootForFile.name
            ) //if has content root added with name
        }

        //If Gherkin or Story file is not valid, thus has just been deleted
        for (contentRoot in contentRoots!!) {
            if (contentRoot!!.hasFileMapped(bddFile.virtualFile)) {
                return contentRoot
            }
        }
        return null //This should never happen given the fact the content root called Rootless should exist
    }

    private val rootless: Optional<ContentRoot?>
        private get() = getContentRoot(ROOTLESS_CONTENT_ROOT_NAME)

    /**
     * Returns the content root for the provided name, or empty Optional if none found.
     *
     * @param moduleName the module name to look for
     */
    @VisibleForTesting
    fun getContentRoot(moduleName: String?): Optional<ContentRoot?> {
        return contentRoots!!.stream()
            .filter { module: ContentRoot? -> module!!.hasName(moduleName) }
            .findFirst()
    }

    private fun getContentRoot(root: Optional<ContentRoot?>, moduleName: String): ContentRoot? {
        return root.orElseGet {
            val contentRoot = ContentRoot(
                moduleName,
                if (GherkinTagsToolWindowSettings.getInstance(project).layout == LayoutType.GROUP_BY_MODULES) ContentRoot.Type.MODULE else ContentRoot.Type.CONTENT_ROOT,
                project
            )
            add(contentRoot)
            contentRoot
        }
    }

    val modules: List<ContentRoot?>
        /**
         * Returns the module type content roots.
         */
        get() = contentRoots!!.stream().filter { obj: ContentRoot? -> obj!!.isModule }
            .collect(Collectors.toList())

    //categories
    override val other: Category
        /**
         * get() is called on the Optional because the category Other should be available.
         */
        get() = findCategory(Category.OTHER_CATEGORY_NAME).get()

    //sort
    override fun sort() {
        if (isProjectDataInitialized) {
            categories!!.forEach(Consumer { obj: Category -> obj.sort() })
            sortIfContainsMultiple(categories!!)
        }
        if (isContentRootDataInitialized) {
            contentRoots!!.forEach(Consumer { obj: ContentRoot? -> obj!!.sort() })
            sortIfContainsMultiple(contentRoots!!)
        }
    }
    //toString
    /**
     * This doesn't show the number of all Gherkin files in the project, only the number of those containing tags.
     */
    override fun toString(): String {
        return displayName
    }

    override fun dispose() {
        if (categories != null) {
            categories!!.forEach(Consumer { obj: Category -> obj.dispose() })
            categories!!.clear()
        }
        if (contentRoots != null) {
            contentRoots!!.forEach(Consumer { obj: ContentRoot? -> obj!!.dispose() })
            contentRoots!!.clear()
        }
    }

    companion object {
        private const val ROOTLESS_CONTENT_ROOT_NAME = "Rootless"
    }
}
