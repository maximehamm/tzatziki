package io.nimbly.tzatziki.generation

import gherkin.formatter.model.Step
import io.nimbly.org.jetbrains.plugins.cucumber.java.steps.TzJavaStepDefinitionCreator
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaExtension
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.text.Normalizer

class TzGherkinJavaExtension : CucumberJavaExtension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzCreateJavaStepDefinition()
    }

    class TzCreateJavaStepDefinition : TzJavaStepDefinitionCreator() {

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

fun String.stripAccents(): String {
    var string = Normalizer.normalize(this, Normalizer.Form.NFD)
    string = Regex("\\p{InCombiningDiacriticalMarks}+").replace(string, "")
    return  string
}

fun String.fixName(): String {
    return if (this == "*") return "When" else this
}