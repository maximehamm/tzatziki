package io.nimbly.tzatziki.generation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import cucumber.runtime.snippets.CamelCaseConcatenator
import cucumber.runtime.snippets.SnippetGenerator
import gherkin.formatter.model.Step
import io.nimbly.org.jetbrains.plugins.cucumber.java.steps.JavaStepDefinitionCreator
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.plugins.cucumber.BDDFrameworkType
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaUtil
import org.jetbrains.plugins.cucumber.java.steps.AnnotationPackageProvider
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.*
import kotlin.collections.ArrayList

/**
 * @see <a href="https://github.com/jlagerweij/cucumber-kotlin/blob/master/src/main/kotlin/net/lagerwey/plugins/cucumber/kotlin/steps/KotlinStepDefinitionCreator.kt">Kotlin plugin</a>
 */
class TzGherkinKotlinExtension : TzGherkinJavaExtension() {

    override fun getStepDefinitionCreator(): StepDefinitionCreator {
        return TzCreateKotlinStepDefinition()
    }

    override fun getStepFileType(): BDDFrameworkType {
        return BDDFrameworkType(KotlinFileType.INSTANCE);
    }

    override fun getStepDefinitionContainers(featureFile: GherkinFile): MutableCollection<out PsiFile> {
        return emptyList<PsiFile>().toMutableList()
    }

    class TzCreateKotlinStepDefinition : JavaStepDefinitionCreator() {

        private var snippetGeneratorCache: SnippetGenerator? = null

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

        override fun createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile {

            val application = ApplicationManager.getApplication()
            var file = application.runWriteAction<KtFile> {
                directory.createFile("$name.kt") as KtFile
            }

            val ktPsiFactory = KtPsiFactory(file.project, markGenerated = true)

            file = application.runWriteAction<KtFile> {
                val psiPackage = directory.getPackage()?.qualifiedName
                if (psiPackage != null)
                    file.packageFqName = FqName(psiPackage)
                file.add(ktPsiFactory.createNewLine(2))

                val ktClass = ktPsiFactory.createClass("""
                    class $name  {
                        
                    }""".trimIndent())
                file.add(ktClass)
                file
            }

            return file
        }

        override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {

            val ktFile = file.ktFile(step)
            if (ktFile == null) {
                snippetGeneratorCache = super.snippetGenerator()
                return super.createStepDefinition(step, file, withTemplate)
            }

            snippetGeneratorCache = SnippetGenerator(KotlinSnippet())

            val ktPsiFactory = KtPsiFactory(file.project, markGenerated = true)
            val ktClass: KtLightClassForSourceDeclaration = (ktFile.classes.firstOrNull() as? KtLightClassForSourceDeclaration) ?: return false
            val ktClassBody = ktClass.kotlinOrigin.body ?: return false
            val generator = snippetGenerator()

            var snippet: String // = generator.getSnippet(cucumberStep, FunctionNameGenerator(CamelCaseConcatenator()))
            if (CucumberJavaUtil.isCucumberExpressionsAvailable(step)) {
                snippet = generator.getSnippet(createStep(FakeStep(step)), TzFunctionNameGenerator(CamelCaseConcatenator()))
                snippet = replaceRegexpWithCucumberExpression(snippet, step.name)
                    .replace("<\\w+>".toRegex(), "{}")
            }
            else {
                snippet = generator.getSnippet(createStep(step), TzFunctionNameGenerator(CamelCaseConcatenator()))
            }

            val annotationPackage = AnnotationPackageProvider().getAnnotationPackageFor(step)
            val stepName = step.keyword.text.fixName()
            val importDirective = ktPsiFactory.createImportDirective(ImportPath.fromString("$annotationPackage.$stepName"))

            snippet = processGeneratedStepDefinition(snippet, step)

            val exp: KtNamedFunction = ktPsiFactory.createFunction(snippet)

            val application = ApplicationManager.getApplication()
            val added = application.runWriteAction<KtNamedFunction> {

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

        private fun PsiFile.ktFile(step: GherkinStep)
                = PsiManager.getInstance(step.project).findFile(virtualFile) as? KtFile

        override fun getDefaultStepDefinitionFolderPath(step: GherkinStep): String {
            val featureFile = step.containingFile
            if (featureFile != null) {
                val psiDirectory = featureFile.containingDirectory
                val project = step.project
                if (psiDirectory != null) {
                    val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
                    val directory = psiDirectory.virtualFile
                    if (projectFileIndex.isInContent(directory)) {
                        var sourceRoot = projectFileIndex.getSourceRootForFile(directory)
                        val module = projectFileIndex.getModuleForFile(featureFile.virtualFile)
                        if (module != null) {
                            val sourceRoots = ModuleRootManager.getInstance(module).sourceRoots
                            if (sourceRoot != null && sourceRoot.name == "resources") {
                                val resourceParent = sourceRoot.parent
                                for (vFile in sourceRoots) {
                                    if (vFile.path.startsWith(resourceParent.path) && vFile.name == "kotlin") {
                                        sourceRoot = vFile
                                        break
                                    }
                                }
                            } else {
                                if (sourceRoots.size > 0) {
                                    sourceRoot = sourceRoots[sourceRoots.size - 1]
                                }
                            }
                        }
                        var packageName = ""
                        if (sourceRoot != null) {
                            packageName = CucumberJavaUtil.getPackageOfStepDef(step)
                        }
                        val packagePath = packageName.replace('.', '/')
                        val path = sourceRoot?.path ?: directory.path
                        return FileUtil.join(path, packagePath)
                    }
                }
            }
            assert(featureFile != null)
            return Objects.requireNonNull(featureFile!!.containingDirectory).virtualFile.path
        }
    }
}