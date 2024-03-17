@file:Suppress("UnstableApiUsage")

package io.nimbly.tzatziki.view.features.nodes

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.TzDataContext
import io.nimbly.tzatziki.util.checkExpression
import io.nimbly.tzatziki.util.emptyConfigurationContext
import io.nimbly.tzatziki.util.getModule
import io.nimbly.tzatziki.util.simpleName
import io.nimbly.tzatziki.util.subModules
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaAllFeaturesInFolderRunConfigurationProducer
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.util.UserDataHolder
import io.nimbly.tzatziki.services.findAllGerkinsFiles

class ModuleNode(
    module: Module,
    val moduleName: String,
    val exp: Expression?
) : AbstractTzNode<Module>(module.project, module, exp), TzRunnableNode {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = moduleName
        presentation.setIcon(ModuleType.get(value).icon)
    }

    override fun getChildren(): List<AbstractTzNode<out UserDataHolder>> {

        val subModules = value.subModules
            .map {  ModuleNode(it, it.simpleName, exp) }

        val subFiles = findAllGerkinsFiles(value)
            .filter { it.checkExpression(filterByTags) }
            .map { GherkinFileNode(project, it, filterByTags) }

        return subModules.sortedBy { it.name } + subFiles.sortedBy { it.name }
    }

    override fun isAlwaysExpand() = true

    override fun getRunConfiguration(): RunConfigurationProducer<*>? {
        val runConfProds = RunConfigurationProducer.getProducers(project)
        return runConfProds.find { it.javaClass == CucumberJavaAllFeaturesInFolderRunConfigurationProducer::class.java }
    }

    override fun getRunDataContext(): ConfigurationContext {
        val dataContext = TzDataContext()
        dataContext.put(CommonDataKeys.PROJECT, project)

        val file = children.filterIsInstance<GherkinFileNode>().firstOrNull()?.file
            ?: return emptyConfigurationContext()

        val fileModule = file.getModule()
        val basePath = fileModule?.guessModuleDir()
        if (basePath != null) {

            val psiDirectory = basePath.toPsiDirectory(project)
            if (psiDirectory != null) {

                dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiDirectory))
                dataContext.put(LangDataKeys.MODULE, fileModule)
            }
        }

        return dataContext.configutation()
    }

    override fun getRunActionText() = "Run Cucumber tests in $moduleName..."

}
