package io.nimbly.tzatziki.view.features.nodes

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.rootModule

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

fun rootNode(project: Project, filterByTags: Expression?): AbstractTzNode<out ComponentManager> {
    return ModuleNode(project.rootModule()!!, filterByTags)
}
