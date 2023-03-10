package io.nimbly.tzatziki.generation

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import gherkin.formatter.model.Step
import io.nimbly.org.jetbrains.plugins.cucumber.java.steps.JavaStepDefinitionCreator
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaExtension
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

open class TzGherkinJavaExtension : CucumberJavaExtension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        val kotlinStepCreator =
            CucumberJvmExtensionPoint.EP_NAME.extensionList
                .find { it.stepFileType.fileType.name == "Kotlin" }

        return kotlinStepCreator?.stepDefinitionCreator
            ?: TzCreateJavaStepDefinition()
    }

    class TzCreateJavaStepDefinition : JavaStepDefinitionCreator() {

        override fun createStep(step: GherkinStep): Step {
            return Step(
                ArrayList(),
                step.keyword.text.fixName(),
                step.name, //.stripAccents(),
                0,
                null,
                null
            )
        }
    }
}