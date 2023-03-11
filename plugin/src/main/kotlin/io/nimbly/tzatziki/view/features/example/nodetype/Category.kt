package io.nimbly.tzatziki.view.features.example.nodetype

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import java.util.*
import java.util.function.Consumer

/**
 * Represents a category of Gherkin tags in the tool window.
 *
 *
 * Such categories may be `Test Suite` for @regression, @smoke and @e2e tags, `Media`
 * for @youtube and @image tags, and any custom one defined by users.
 *
 *
 * Such categorization makes it easier to find tags and to better overview the types of tags defined.
 *
 *
 * In case of grouped layouts in the tool window, Category objects with the same name but with different/overlapping tags
 * may be assigned to different modules, content roots, etc.
 */
class Category(displayName: String, project: Project) : AbstractNodeType(displayName, project) {

    val tags: MutableList<Tag> = SmartList()

    operator fun get(tagName: String): Optional<Tag?> {
        return tags.stream().filter { tag: Tag? -> tag!!.hasName(tagName) }.findFirst()
    }

    fun add(tag: Tag): Category {
        tags.add(tag)
        return this
    }

    fun hasTag(): Boolean {
        return !tags.isEmpty()
    }

    fun addTagOrFileToTag(tagNameWithoutAt: String, file: VirtualFile): Category {
        get(tagNameWithoutAt).ifPresentOrElse({ tag: Tag? -> tag!!.add(file) }) {
            tags.add(
                Tag(
                    tagNameWithoutAt,
                    file,
                    project
                )
            )
        }
        return this
    }

    val isOther: Boolean
        get() = OTHER_CATEGORY_NAME == displayName

    val isNotOtherAndDoesntHaveAnyTag: Boolean
        get() = !isOther && tags.isEmpty()

    override fun sort() {
        tags.forEach(Consumer { obj: Tag? -> obj!!.sort() })
        sortIfContainsMultiple(tags)
    }

    override fun toString(): String {
        return displayName
    }

    override fun dispose() {
        tags.forEach(Consumer { obj: Tag? -> obj!!.dispose() })
        tags.clear()
    }

    companion object {
        const val OTHER_CATEGORY_NAME = "Other"
        fun createOther(project: Project): Category {
            return Category(OTHER_CATEGORY_NAME, project)
        }
    }
}
