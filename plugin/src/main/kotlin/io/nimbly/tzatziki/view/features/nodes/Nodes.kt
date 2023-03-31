@file:Suppress("UnstableApiUsage")

package io.nimbly.tzatziki.view.features.nodes

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.rootModule
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

interface TzRunnableNode {
    fun getRunConfiguration(): RunConfigurationProducer<*>?
    fun getRunDataContext(): ConfigurationContext
    fun getRunActionText(): String
}

interface TzPrintable {
}

abstract class AbstractTzPsiElementNode<T: PsiElement>(
    p: Project,
    value: T,
    filterByTags: Expression?
) : AbstractTzNode<T>(p, value, filterByTags)

abstract class AbstractTzNode<T: Any>(
    p: Project,
    value: T,
    var filterByTags: Expression?
) : AbstractTreeNode<T>(p, value), Navigatable

fun createModuleNode(
    project: Project,
    filterByTags: Expression?,
    fromModule: Module? = null
): ModuleNode {

    val module: Module
    val path: List<String>
    if (fromModule == null) {
        module = project.rootModule()!!
        path = listOf(project.name)
    }
    else {
        module = fromModule
        path = ModuleGrouper.instanceFor(project).getGroupPath(module)
    }

    val name = path.last()
    return ModuleNode(module, name, path, filterByTags)
}
