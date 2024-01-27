package io.nimbly.tzatziki.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateAllStepsFix
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateStepFix
import org.jetbrains.plugins.cucumber.inspections.CucumberStepDefinitionCreationContext
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper
import javax.swing.Icon
import javax.swing.SwingConstants

// See https://github.com/JetBrains/intellij-plugins/blob/master/cucumber/src/org/jetbrains/plugins/cucumber/inspections/CucumberCreateStepFix.java
interface TzCucumberCreateStepFixInterface {
    fun createStepOrSteps(step: GherkinStep, selectedValue: CucumberStepDefinitionCreationContext)
}

class TzCucumberCreateStepFix : CucumberCreateStepFix(), TzCucumberCreateStepFixInterface {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        tzAppliFix(descriptor, this)
    }
    override fun createStepOrSteps(step: GherkinStep, selectedValue: CucumberStepDefinitionCreationContext) {
        return super.createStepOrSteps(step, selectedValue)
    }
}

class TzCucumberCreateAllStepsFix : CucumberCreateAllStepsFix(), TzCucumberCreateStepFixInterface {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        tzAppliFix(descriptor, this)
    }
    override fun createStepOrSteps(step: GherkinStep, selectedValue: CucumberStepDefinitionCreationContext) {
        return super.createStepOrSteps(step, selectedValue)
    }
}


fun tzAppliFix(descriptor: ProblemDescriptor, fix: TzCucumberCreateStepFixInterface) {
    val step = descriptor.psiElement as GherkinStep
    val featureFile = step.containingFile as GherkinFile

    val pairs = getAndFixStepDefinitionContainers(featureFile).toMutableList()
    if (pairs.isNotEmpty()) {

        pairs.add(0, CucumberStepDefinitionCreationContext())
        val popupFactory = JBPopupFactory.getInstance()
        val popupStep =
            popupFactory.createListPopup(object : BaseListPopupStep<CucumberStepDefinitionCreationContext>(
                CucumberBundle.message("choose.step.definition.file"),
                ArrayList<CucumberStepDefinitionCreationContext>(pairs)
            ) {
                override fun isSpeedSearchEnabled(): Boolean {
                    return true
                }

                @NotNull
                override fun getTextFor(value: CucumberStepDefinitionCreationContext): String {
                    if (value.psiFile == null) {
                        return CucumberBundle.message("create.new.file")
                    }
                    val psiFile = value.psiFile
                    val file: VirtualFile = value.psiFile!!.virtualFile!!
                    val stepDefinitionCreator = CucumberStepHelper.getExtensionMap()[value.frameworkType]!!
                        .stepDefinitionCreator
                    return stepDefinitionCreator.getStepDefinitionFilePath(psiFile!!)
                }

                override fun getIconFor(value: CucumberStepDefinitionCreationContext): Icon? {
                    val psiFile = value.psiFile
                    return if (psiFile == null) AllIcons.Actions.IntentionBulb else psiFile.getIcon(0)
                }

                override fun onChosen(
                    selectedValue: CucumberStepDefinitionCreationContext,
                    finalChoice: Boolean
                ): PopupStep<*>? {
                    return doFinalStep { fix.createStepOrSteps(step, selectedValue) }
                }
            })

        popupStep.setAdText("Cucumber+", SwingConstants.CENTER)
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            popupStep.showCenteredInCurrentWindow(step.project)
        } else {
            fix.createStepOrSteps(step, pairs[1])
        }
    } else {
        fix.createStepOrSteps(step, CucumberStepDefinitionCreationContext())
    }
}

fun getAndFixStepDefinitionContainers(featureFile: GherkinFile): Set<CucumberStepDefinitionCreationContext> {

    val result = CucumberStepHelper
        .getStepDefinitionContainers(featureFile)
        .filter { (psiFile, frameworkType): CucumberStepDefinitionCreationContext ->
            val ext = CucumberStepHelper.getExtensionMap()[frameworkType]
            ext != null
                    && ext.stepFileType.fileType == psiFile?.fileType
        }

    return result.toSet()
}
