package io.nimbly.tzatziki.generation

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJava8Extension
import org.jetbrains.plugins.cucumber.java.steps.Java8StepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepImpl

class TzGherkinJava8Extension : CucumberJava8Extension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzcreateStepDefinition()
    }

    private class TzcreateStepDefinition : Java8StepDefinitionCreator() {
        override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {
            val stepX = if (step is GherkinStepImpl) TzGherkinStep(step) else step
            return super.createStepDefinition(stepX, file, withTemplate)
        }
    }

}
