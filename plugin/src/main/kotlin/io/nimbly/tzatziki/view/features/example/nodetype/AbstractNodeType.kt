package io.nimbly.tzatziki.view.features.example.nodetype

import com.intellij.openapi.project.Project
import io.nimbly.tzatziki.view.features.example.GherkinTagsToolWindowSettings
import io.nimbly.tzatziki.view.features.example.StatisticsType
import java.util.*
import java.util.function.Supplier

/**
 * Stores common properties of nodes.
 */
abstract class AbstractNodeType protected constructor(var displayName: String, protected val project: Project) :
    NodeType {
    /**
     * Return a toString value based on what type of statistics should be displayed in the Gherkin Tags tool window.
     *
     * @param simplified the toString supplier for the Simplified statistics
     * @param detailed   the toString supplier for the Detailed statistics
     */
    fun getToString(simplified: Supplier<String?>, detailed: Supplier<String?>): String? {
        var toString: String? = displayName
        when (GherkinTagsToolWindowSettings.getInstance(project).statisticsType) {
            StatisticsType.SIMPLIFIED -> toString = simplified.get()
            StatisticsType.DETAILED -> toString = detailed.get()
            else -> {}
        }
        return toString
    }

    /**
     * Returns true if this node has the given name, false otherwise.
     */
    fun hasName(name: String?): Boolean {
        return name != null && name == displayName
    }

    /**
     * Sorts the argument list of elements based on the elements' display names if it contains more than one element.
     *
     * @param elements the list of elements to sort
     */
    protected fun sortIfContainsMultiple(elements: List<AbstractNodeType>) {
        if (elements.size > 1) {
            elements.sortedWith(Comparator.comparing { element-> element.displayName.lowercase(Locale.getDefault()) })
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as AbstractNodeType
        return displayName == that.displayName
    }

    override fun hashCode(): Int {
        return Objects.hash(displayName)
    }
}
