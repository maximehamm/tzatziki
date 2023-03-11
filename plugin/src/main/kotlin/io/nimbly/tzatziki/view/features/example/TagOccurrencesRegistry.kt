package io.nimbly.tzatziki.view.features.example

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.apache.commons.lang3.mutable.MutableInt
import java.util.*

@Service(Service.Level.PROJECT)
class TagOccurrencesRegistry(private val project: Project) : Disposable {
    /**
     * FeatureFile path -> &lt;tag name, count>
     */
    private var tagOccurrences: MutableMap<String, MutableMap<String, MutableInt>>? = null

    /**
     * Initializes the map according to the number of Gherkin and Story files in the project to minimize the allocation size.
     */
    fun init(bddFileCount: Int) {
        tagOccurrences = HashMap(bddFileCount)
    }


    fun getCountFor(path: String, tag: String): Int {
        return Optional.ofNullable<Map<String, MutableInt>>(
            tagOccurrences!![path]
        )
            .map { tagToCountForPath: Map<String, MutableInt> -> tagToCountForPath[tag] }
            .map { obj: MutableInt? -> obj!!.toInt() }
            .orElse(0)
    }

    override fun dispose() {
        tagOccurrences!!.clear()
        tagOccurrences = null
    }

    companion object {
        fun getInstance(project: Project): TagOccurrencesRegistry {
            return project.getService(TagOccurrencesRegistry::class.java)
        }
    }
}
