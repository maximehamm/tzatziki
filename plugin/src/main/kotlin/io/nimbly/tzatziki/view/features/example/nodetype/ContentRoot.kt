package io.nimbly.tzatziki.view.features.example.nodetype

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import java.util.Map
import java.util.function.Consumer
import javax.swing.Icon

/**
 * Represents a general content root, be it a project module, sources root, resources root, or other type of content root
 * identified by the IDE.
 */
class ContentRoot(displayName: String, val type: Type, project: Project) : AbstractNodeType(displayName, project),
    CategoriesHolder {

    override val categories: MutableList<Category> = SmartList()

    /**
     * Gets the category dedicated for unmapped tags.
     */
    override val other: Category

    /**
     * It initializes the collection of categories with one called `Other`, where unmapped tags will be put.
     */
    init {
        other = Category.createOther(project)
        categories.add(other)
    }

    val icon: Icon?
        get() = ICONS[type]
    val isModule: Boolean
        get() = type == Type.MODULE
    val isContentRoot: Boolean
        get() = type == Type.CONTENT_ROOT

    /**
     * Gets whether this content root node has the argument file stored in any of its underlying tags.
     *
     * @param file the file to look for
     * @return true if the file is stored, false otherwise
     */
    fun hasFileMapped(file: VirtualFile): Boolean {
        return categories.stream()
            .flatMap { category -> category.tags.stream() }
            .flatMap { tag -> tag.featureFiles.stream() }
            .anyMatch { featureFile -> featureFile.file == file }
    }

    /**
     * Sorts each collection of categories, tags and virtual files alphabetically.
     */
    override fun sort() {
        categories.forEach(Consumer { obj: Category -> obj.sort() })
        sortIfContainsMultiple(categories)
    }

    /**
     * This doesn't show the number of all Gherkin files in the project, only the number of those containing tags.
     */
    override fun toString(): String {
        return displayName
    }

    override fun dispose() {
        categories.forEach(Consumer { obj: Category -> obj.dispose() })
        categories.clear()
    }

    /**
     * The type of the content root.
     */
    enum class Type {
        MODULE,
        CONTENT_ROOT
    }

    companion object {
        private val ICONS = Map.of(
            Type.MODULE,
            AllIcons.Actions.ModuleDirectory,
            Type.CONTENT_ROOT,
            AllIcons.Modules.ResourcesRoot //TODO: modify icon based on the root is a resource, test resource or other non-source root.
        )
    }
}
