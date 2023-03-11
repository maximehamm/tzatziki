package io.nimbly.tzatziki.view.features.example

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Stores the project specific settings for the Gherkin Tags tool window.
 *
 * @since 0.1.0
 */
@State(name = "Gherkin Overview Tags Tool Window Settings", storages = [Storage("GherkinTagsToolWindowSettings.xml")])
@Service(
    Service.Level.PROJECT
)
class GherkinTagsToolWindowSettings : PersistentStateComponent<GherkinTagsToolWindowSettings?> {
    /**
     * Stores the type of statistics to display in the tool window.
     *
     * @since 0.1.0
     */
    var statisticsType = StatisticsType.DISABLED

    /**
     * Whether to show data grouped.
     *
     * @since 0.1.0
     */
    var layout = LayoutType.NO_GROUPING
    override fun getState(): GherkinTagsToolWindowSettings? {
        return this
    }

    override fun loadState(state: GherkinTagsToolWindowSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): GherkinTagsToolWindowSettings {
            return project.getService(GherkinTagsToolWindowSettings::class.java)
        }
    }
}
