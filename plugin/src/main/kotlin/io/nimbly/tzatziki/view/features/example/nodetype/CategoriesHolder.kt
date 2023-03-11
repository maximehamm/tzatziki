package io.nimbly.tzatziki.view.features.example.nodetype

import java.util.*

/**
 * Marks tree node types that they store and manage [Category] objects and information.
 *
 * @see ModelDataRoot
 *
 * @see ContentRoot
 */
interface CategoriesHolder {
    /**
     * Gets the category with the provided name or empty Optional if none found.
     *
     * @param name the category name to find
     * @return the category, or empty Optional
     */
    fun findCategory(name: String?): Optional<Category> {
        return categories!!.stream().filter { category: Category -> category.hasName(name) }
            .findFirst()
    }

    /**
     * Gets the dedicated catch-all category called "Other".
     */
    val other: Category

    /**
     * Gets the category with the provided name, or the catch-all Other category if none is found with that name.
     *
     * @param name the category name to find
     * @return the found category, or Other
     */
    fun findCategoryOrOther(name: String?): Category {
        return findCategory(name).orElse(other)
    }

    /**
     * Gets the list of categories stored by this node.
     */
    val categories: MutableList<Category>

    /**
     * Adds the argument category to this node.
     *
     *
     * It doesn't do any check whether the category is already added to it. Ideally it shouldn't happen that one
     * Category object is added more than once.
     *
     * @param category the category to add
     */
    fun <T : CategoriesHolder> addCategory(category: Category): T {
        categories.add(category)
        return this as T
    }

    /**
     * Queries the tag with the provided name in the underlying model data.
     *
     *
     * It returns the Tag as an Optional, or an empty Optional if no tag is found with the given name.
     *
     * @param tagName the tag's name to search for
     */
    fun findTag(tagName: String?): Optional<Tag?> {
        return categories!!.stream().flatMap { category: Category -> category.tags.stream() }
            .filter { tag: Tag? -> tag!!.hasName(tagName) }.findFirst()
    }
}
