package io.nimbly.tzatziki.generation

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepImpl
import java.text.Normalizer

class TzGherkinStep(private val step: GherkinStepImpl) : GherkinStep by step {

    private val fakeKeyWord : ASTNode

    init {
        val tp = CucumberElementFactory.createTempPsiFile(step.project, "When");
        fakeKeyWord = tp.firstChild.node
    }

    override fun getKeyword(): ASTNode? {
        return fakeKeyWord
    }

    override fun getName(): String {
        return step.name.stripAccents()
    }

    fun String.stripAccents(): String {
        var string = Normalizer.normalize(this, Normalizer.Form.NFD)
        string = Regex("\\p{InCombiningDiacriticalMarks}+").replace(string, "")
        return  string
    }
}