package io.nimbly.tzatziki.view.features.example

import com.intellij.openapi.project.Project
import io.nimbly.tzatziki.view.features.example.model.ContentRootBasedGherkinTagTreeModel
import io.nimbly.tzatziki.view.features.example.model.GherkinTagTreeModel
import io.nimbly.tzatziki.view.features.example.model.ProjectSpecificGherkinTagTreeModel
import io.nimbly.tzatziki.view.features.example.nodetype.ModelDataRoot
import java.util.Map
import java.util.function.BiFunction
import java.util.function.Function

class _ExTreeModelFactory {

    fun createTreeModel(project: Project): GherkinTagTreeModel {
        return MODELS[GherkinTagsToolWindowSettings.Companion.getInstance(project).layout]!!
            .apply(project)
    }

    fun createTreeModel(data: ModelDataRoot, project: Project): GherkinTagTreeModel {
        return COPIED_MODELS[GherkinTagsToolWindowSettings.Companion.getInstance(project).layout]!!
            .apply(data, project)
    }

    companion object {

        private val MODELS = Map.of(
            LayoutType.NO_GROUPING,
            Function<Project, GherkinTagTreeModel> { project: Project -> ProjectSpecificGherkinTagTreeModel(project) },
            LayoutType.GROUP_BY_MODULES,
            Function<Project, GherkinTagTreeModel> { project: Project -> ContentRootBasedGherkinTagTreeModel(project) })

        private val COPIED_MODELS = Map.of(
            LayoutType.NO_GROUPING,
            BiFunction<ModelDataRoot, Project, GherkinTagTreeModel> { data: ModelDataRoot, project: Project ->
                ProjectSpecificGherkinTagTreeModel(data, project)
            },
            LayoutType.GROUP_BY_MODULES,
            BiFunction<ModelDataRoot, Project, GherkinTagTreeModel> { data: ModelDataRoot, project: Project ->
                ContentRootBasedGherkinTagTreeModel(data, project)
            })
    }
}
