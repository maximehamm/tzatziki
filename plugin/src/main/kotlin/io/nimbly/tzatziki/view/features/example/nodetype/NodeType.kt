package io.nimbly.tzatziki.view.features.example.nodetype

import com.intellij.openapi.Disposable

/**
 * Node type for the elements of the Gherkin tree.
 */
interface NodeType : Sortable, Disposable {
    override fun dispose() {}

    companion object {
        /**
         * Returns the argument object as a [ContentRoot].
         */
        fun asContentRoot(node: Any): ContentRoot {
            return node as ContentRoot
        }

        /**
         * Returns the argument object as a [Category].
         */
        fun asCategory(node: Any): Category {
            return node as Category
        }

        /**
         * Returns the argument object as a [Tag].
         */
        fun asTag(node: Any): Tag {
            return node as Tag
        }
    }
}
