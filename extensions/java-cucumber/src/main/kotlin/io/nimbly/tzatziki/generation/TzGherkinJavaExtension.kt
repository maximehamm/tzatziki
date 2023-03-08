package io.nimbly.tzatziki.generation

import gherkin.formatter.model.Step
import io.nimbly.org.jetbrains.plugins.cucumber.java.steps.JavaStepDefinitionCreator
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaExtension
import org.jetbrains.plugins.cucumber.psi.GherkinStep

open class TzGherkinJavaExtension : CucumberJavaExtension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzCreateJavaStepDefinition()
    }

    class TzCreateJavaStepDefinition : JavaStepDefinitionCreator() {

        override fun createStep(step: GherkinStep): Step {
            return Step(
                ArrayList(),
                step.keyword.text.fixName(),
                step.name.stripAccents(),
                0,
                null,
                null
            )
        }
    }
}