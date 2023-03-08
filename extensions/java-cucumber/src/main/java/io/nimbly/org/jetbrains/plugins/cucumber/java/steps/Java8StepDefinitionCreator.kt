package io.nimbly.org.jetbrains.plugins.cucumber.java.steps

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.lang.Language
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.tree.Factory
import com.intellij.psi.util.PsiTreeUtil
import cucumber.runtime.snippets.CamelCaseConcatenator
import cucumber.runtime.snippets.FunctionNameGenerator
import cucumber.runtime.snippets.SnippetGenerator
import org.jetbrains.plugins.cucumber.java.steps.Java8Snippet
import org.jetbrains.plugins.cucumber.psi.GherkinStep


open class Java8StepDefinitionCreator : JavaStepDefinitionCreator() {

    override fun createStepDefinitionContainer(dir: PsiDirectory, name: String): PsiFile {
        val result = super.createStepDefinitionContainer(dir, name)
        val fileIndex = ProjectRootManager.getInstance(dir.project).fileIndex
        val module = fileIndex.getModuleForFile(result.virtualFile)!!
        val dependenciesScope = module.getModuleWithDependenciesAndLibrariesScope(true)
        val stepDefContainerInterface =
            JavaPsiFacade.getInstance(module.project).findClass(CUCUMBER_API_JAVA8_EN, dependenciesScope)
        if (stepDefContainerInterface != null) {
            val createPsiClass = PsiTreeUtil.getChildOfType(
                result,
                PsiClass::class.java
            )!!
            val elementFactory = JavaPsiFacade.getInstance(dir.project).elementFactory
            val ref = elementFactory.createClassReferenceElement(stepDefContainerInterface)
            if (stepDefContainerInterface.isInterface) {
                val implementsList = createPsiClass.implementsList
                if (implementsList != null) {
                    WriteAction.run<RuntimeException> { implementsList.add(ref) }
                }
            }
        }
        return result
    }

    override fun getStepDefinitionFilePath(file: PsiFile): String {
        return super.getStepDefinitionFilePath(file) + " (Java 8 style)"
    }

    override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {

        if (file !is PsiClassOwner) return false
        val clazz = PsiTreeUtil.getChildOfType(
            file,
            PsiClass::class.java
        ) ?: return false
        val project = file.getProject()
        closeActiveTemplateBuilders(file)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val stepDef = buildStepDefinitionByStep(step, file.getLanguage())

        val constructor = getConstructor(clazz)
        val constructorBody = constructor.body ?: return false
        var anchor = constructorBody.firstChild
        if (constructorBody.statements.size > 0) {
            anchor = constructorBody.statements[constructorBody.statements.size - 1]
        }
        var addedStepDef = constructorBody.addAfter(stepDef, anchor)
        wrapStepDefWithLineBreakAndSemicolon(addedStepDef)
        addedStepDef = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(addedStepDef)
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedStepDef!!)
        val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        if (addedStepDef !is PsiMethodCallExpression)
            return false
        if (addedStepDef.argumentList.expressions.size < 2)
            return false
        val regexpElement: PsiExpression = addedStepDef.argumentList.expressions[0]
        val secondArgument = addedStepDef.argumentList.expressions[1] as? PsiLambdaExpression
            ?: return false
        val blockVars: PsiParameterList = secondArgument.parameterList
        val lambdaBody = secondArgument.body as? PsiCodeBlock
            ?: return false
        if (withTemplate) {
            runTemplateBuilderOnAddedStep(editor, addedStepDef, regexpElement, blockVars, lambdaBody)
        }
        return true
    }

    protected fun buildStepDefinitionByStep(step: GherkinStep, language: Language): PsiElement {

        val cucumberStep = createStep(step)
        val generator = SnippetGenerator(Java8Snippet())
        val snippetTemplate = generator.getSnippet(cucumberStep, FunctionNameGenerator(CamelCaseConcatenator()))
        val snippet = processGeneratedStepDefinition(snippetTemplate, step)
        val factory = JVMElementFactories.requireFactory(language, step.project)
        val expression = factory.createExpressionFromText(snippet, step)
        return try {
            createStepDefinitionFromSnippet(expression, step, factory)
        } catch (e: Exception) {
            expression
        }
    }


    protected fun wrapStepDefWithLineBreakAndSemicolon(addedStepDef: PsiElement?) {
        val linebreak = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n", 0, 1, null, addedStepDef!!.manager)
        addedStepDef.parent.addBefore(linebreak.psi, addedStepDef)
        val semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, addedStepDef.manager)
        addedStepDef.parent.addAfter(semicolon.psi, addedStepDef)
    }

    companion object {
        const val CUCUMBER_API_JAVA8_EN = "cucumber.api.java8.En"
        private const val FILE_TEMPLATE_CUCUMBER_JAVA_8_STEP_DEFINITION_JAVA = "Cucumber Java 8 Step Definition.java"
        private fun getConstructor(clazz: PsiClass): PsiMethod {
            if (clazz.constructors.size == 0) {
                val factory = JVMElementFactories.requireFactory(clazz.language, clazz.project)
                val constructor = factory.createConstructor(clazz.name!!)
                return clazz.add(constructor) as PsiMethod
            }
            return clazz.constructors[0]
        }

        private fun createStepDefinitionFromSnippet(
            snippetExpression: PsiElement, step: GherkinStep,
            factory: JVMElementFactory
        ): PsiElement {
            val callExpression = snippetExpression as PsiMethodCallExpression
            val arguments = callExpression.argumentList.expressions
            val lambda = arguments[1] as PsiLambdaExpression
            val fileTemplateDescriptor = FileTemplateDescriptor(FILE_TEMPLATE_CUCUMBER_JAVA_8_STEP_DEFINITION_JAVA)
            val fileTemplate = FileTemplateManager.getInstance(snippetExpression.getProject())
                .getCodeTemplate(fileTemplateDescriptor.fileName)
            var text = fileTemplate.text.replace("\${STEP_KEYWORD}", callExpression.methodExpression.text)
                .replace("\${STEP_REGEXP}", arguments[0].text)
                .replace("\${PARAMETERS}", lambda.parameterList.text)
                .replace("\${BODY}\n", "")
            text = processGeneratedStepDefinition(
                text, snippetExpression
            )
            return factory.createExpressionFromText(text, step)
        }
    }

}
