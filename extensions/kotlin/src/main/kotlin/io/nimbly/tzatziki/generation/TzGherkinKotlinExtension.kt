package io.nimbly.tzatziki.generation

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import cucumber.runtime.snippets.CamelCaseConcatenator
import cucumber.runtime.snippets.FunctionNameGenerator
import cucumber.runtime.snippets.SnippetGenerator
import gherkin.formatter.model.Step
import io.nimbly.org.jetbrains.plugins.cucumber.java.steps.JavaStepDefinitionCreator
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaUtil
import org.jetbrains.plugins.cucumber.java.steps.AnnotationPackageProvider
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * @see <a href="https://github.com/jlagerweij/cucumber-kotlin/blob/master/src/main/kotlin/net/lagerwey/plugins/cucumber/kotlin/steps/KotlinStepDefinitionCreator.kt">Kotlin plugin</a>
 */
class TzGherkinKotlinExtension : TzGherkinJavaExtension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzCreateKotlinStepDefinition()
    }

    class TzCreateKotlinStepDefinition : JavaStepDefinitionCreator() {

        private var snippetGeneratorCache: SnippetGenerator? = null

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

        override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {

            val ktFile = PsiManager.getInstance(step.project).findFile(file.virtualFile) as? KtFile
            if (ktFile == null) {
                snippetGeneratorCache = super.snippetGenerator()
                return super.createStepDefinition(step, file, withTemplate)
            }

            snippetGeneratorCache = SnippetGenerator(KotlinSnippet())

            val ktPsiFactory = KtPsiFactory(file.project, markGenerated = true)
            val ktClass: KtLightClassForSourceDeclaration = (ktFile.classes.firstOrNull() as? KtLightClassForSourceDeclaration) ?: return false
            val ktClassBody = ktClass.kotlinOrigin.body ?: return false
            val cucumberStep = createStep(step)
            val generator = snippetGenerator()
            var snippet = generator.getSnippet(cucumberStep, FunctionNameGenerator(CamelCaseConcatenator()))
            if (CucumberJavaUtil.isCucumberExpressionsAvailable(step)) {
                snippet = replaceRegexpWithCucumberExpression(snippet, step.name)
            }

            val annotationPackage = AnnotationPackageProvider().getAnnotationPackageFor(step)
            val stepName = step.keyword.text.fixName()
            val importDirective = ktPsiFactory.createImportDirective(ImportPath.fromString("$annotationPackage.$stepName"))

            snippet = processGeneratedStepDefinition(snippet, step)

            val exp: KtNamedFunction = ktPsiFactory.createFunction(snippet)

            val added = runWriteAction {

                val importList = ktFile.importList
                if (importList != null && !importList.imports.map { it.importPath }.contains(importDirective.importPath))
                    importList.add(importDirective)

                val added = ktClassBody.addBefore(exp, ktClassBody.rBrace)
                added as KtNamedFunction
            }

            added.navigate(true)
            return true
        }

        override fun snippetGenerator(): SnippetGenerator {
            return snippetGeneratorCache ?: super.snippetGenerator()
        }

    }
}