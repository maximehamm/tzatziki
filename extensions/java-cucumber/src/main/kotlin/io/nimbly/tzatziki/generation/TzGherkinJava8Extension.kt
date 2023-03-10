package io.nimbly.tzatziki.generation

import com.intellij.psi.PsiFile
import gherkin.formatter.model.Step
import io.nimbly.org.jetbrains.plugins.cucumber.java.steps.Java8StepDefinitionCreator
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJava8Extension
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzGherkinJava8Extension : CucumberJava8Extension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzCreateJava8StepDefinition()
    }

    override fun getStepDefinitionContainers(featureFile: GherkinFile): MutableCollection<out PsiFile> {
        val all = super.getStepDefinitionContainers(featureFile)
        return all
            .filter { it.fileType == this.stepFileType.fileType }
            .toMutableList()
    }

    class TzCreateJava8StepDefinition : Java8StepDefinitionCreator() {

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
