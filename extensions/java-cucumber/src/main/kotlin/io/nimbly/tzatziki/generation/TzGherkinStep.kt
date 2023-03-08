package io.nimbly.tzatziki.generation

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.cucumber.CucumberElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepImpl
import javax.lang.model.SourceVersion

class TzGherkinStep(private val step: GherkinStepImpl) : GherkinStep by step {

    private val fakeKeyWord : ASTNode

    init {
        val tp = CucumberElementFactory.createTempPsiFile(step.project, "When");
        fakeKeyWord = tp.firstChild.node
    }

    override fun getKeyword(): ASTNode? {
        return fakeKeyWord
    }

//    override fun getName(): String {
////        SourceVersion.isIdentifier("xxx")
////        Character.isJavaIdentifierPart()
//        return step.name
//    }
}