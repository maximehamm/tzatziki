package io.nimbly.tzatziki.generation

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaExtension
import org.jetbrains.plugins.cucumber.java.steps.JavaStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepImpl

class TzGherkinJavaExtension : CucumberJavaExtension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzcreateStepDefinition()
    }

    private class TzcreateStepDefinition : JavaStepDefinitionCreator() {
        override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {
            val stepX = if (step is GherkinStepImpl) TzGherkinStep(step) else step
            return super.createStepDefinition(stepX, file, withTemplate)x
        }
    }

}
