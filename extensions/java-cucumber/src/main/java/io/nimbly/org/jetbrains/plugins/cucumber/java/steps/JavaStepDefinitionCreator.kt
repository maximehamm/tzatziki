package io.nimbly.org.jetbrains.plugins.cucumber.java.steps

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.template.*
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.CreateClassUtil
import com.intellij.psi.util.PsiTreeUtil
import cucumber.runtime.snippets.CamelCaseConcatenator
import cucumber.runtime.snippets.FunctionNameGenerator
import cucumber.runtime.snippets.SnippetGenerator
import gherkin.formatter.model.Step
import io.cucumber.cucumberexpressions.CucumberExpressionGenerator
import io.cucumber.cucumberexpressions.ParameterTypeRegistry
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.java.CucumberJavaUtil
import org.jetbrains.plugins.cucumber.java.steps.AnnotationPackageProvider
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.*

open class TzJavaStepDefinitionCreator : AbstractStepDefinitionCreator() {
    override fun createStepDefinitionContainer(dir: PsiDirectory, name: String): PsiFile {
        val newClass = CreateClassUtil.createClassNamed(
            name,
            CreateClassUtil.DEFAULT_CLASS_TEMPLATE,
            dir
        )!!
        return newClass.containingFile
    }

    override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {
        if (file !is PsiClassOwner) return false
        val project = file.getProject()
        closeActiveTemplateBuilders(file)
        val clazz = PsiTreeUtil.getChildOfType(file, PsiClass::class.java)
        if (clazz != null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            // snippet text
            val element = buildStepDefinitionByStep(step, file)
            var addedElement: PsiMethod? = clazz.add(element) as PsiMethod
            addedElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(addedElement!!)
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedElement!!)
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            assert(editor != null)
            val blockVars = addedElement.parameterList
            val body = addedElement.body
            val annotation = addedElement.modifierList.annotations[0]
            val regexpElement: PsiElement = annotation.parameterList.attributes[0]
            if (withTemplate) {
                runTemplateBuilderOnAddedStep(editor!!, addedElement, regexpElement, blockVars, body)
            }
        }
        return true
    }

    fun runTemplateBuilderOnAddedStep(
        editor: Editor,
        addedElement: PsiElement,
        regexpElement: PsiElement,
        blockVars: PsiParameterList,
        body: PsiCodeBlock?
    ) {
        val project = regexpElement.project
        val builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(addedElement) as TemplateBuilderImpl
        val range = TextRange(1, regexpElement.textLength - 1)
        builder.replaceElement(regexpElement, range, range.substring(regexpElement.text))
        for (`var` in blockVars.parameters) {
            val nameIdentifier: PsiElement? = `var`.nameIdentifier
            if (nameIdentifier != null) {
                builder.replaceElement(nameIdentifier, nameIdentifier.text)
            }
        }
        if (body!!.statements.size > 0) {
            val firstStatement: PsiElement = body.statements[0]
            val pendingRange = TextRange(0, firstStatement.textLength - 1)
            builder.replaceElement(
                firstStatement, pendingRange,
                pendingRange.substring(firstStatement.text)
            )
        }
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.doPostponedOperationsAndUnblockDocument(editor.document)
        val template = builder.buildInlineTemplate()
        editor.caretModel.moveToOffset(addedElement.textRange.startOffset)
        val adapter: TemplateEditingAdapter = object : TemplateEditingAdapter() {
            override fun templateFinished(template: Template, brokenOff: Boolean) {
                ApplicationManager.getApplication().runWriteAction {
                    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                        ?: return@runWriteAction
                    val offset = editor.caretModel.offset - 1
                    var codeBlock: PsiCodeBlock? = null
                    val lambda =
                        PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiLambdaExpression::class.java, false)
                    if (lambda != null) {
                        val body = lambda.body
                        codeBlock = if (body is PsiCodeBlock) body else null
                    }
                    if (codeBlock == null) {
                        val method =
                            PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiMethod::class.java, false)
                        if (method != null) {
                            codeBlock = method.body
                        }
                    }
                    if (codeBlock != null) {
                        CreateFromUsageUtils.setupEditor(codeBlock, editor)
                    }
                }
            }
        }
        TemplateManager.getInstance(project).startTemplate(editor, template, adapter)
    }

    override fun validateNewStepDefinitionFileName(project: Project, name: String): Boolean {
        if (name.length == 0) return false
        if (!Character.isJavaIdentifierStart(name[0])) return false
        for (i in 1 until name.length) {
            if (!Character.isJavaIdentifierPart(name[i])) return false
        }
        return true
    }

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
                                if (vFile.path.startsWith(resourceParent.path) && vFile.name == "java") {
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

    override fun getStepDefinitionFilePath(file: PsiFile): String {
        val vFile = file.virtualFile
        if (file is PsiClassOwner && vFile != null) {
            val packageName = file.packageName
            return if (StringUtil.isEmptyOrSpaces(packageName)) {
                vFile.nameWithoutExtension
            } else {
                vFile.nameWithoutExtension + " (" + packageName + ")"
            }
        }
        return file.name
    }

    override fun getDefaultStepFileName(step: GherkinStep): String {
        return STEP_DEFINITION_SUFFIX
    }

    protected fun buildStepDefinitionByStep(step: GherkinStep, file: PsiFile): PsiMethod {
        val language = file.language
        val annotationPackage = AnnotationPackageProvider().getAnnotationPackageFor(step)
        val methodAnnotation = String.format("@%s.", annotationPackage)
        val cucumberStep = createStep(step)
        val generator = SnippetGenerator(JavaSnippet())
        var snippet = generator.getSnippet(cucumberStep, FunctionNameGenerator(CamelCaseConcatenator()))
        if (CucumberJavaUtil.isCucumberExpressionsAvailable(step)) {
            snippet = replaceRegexpWithCucumberExpression(snippet, step.name)
        }
        snippet = snippet.replaceFirst("@".toRegex(), methodAnnotation)
        snippet = processGeneratedStepDefinition(snippet, step)
        val factory = JVMElementFactories.requireFactory(language, step.project)
        val methodFromCucumberLibraryTemplate = factory.createMethodFromText(snippet, step)
        return try {
            createStepDefinitionFromSnippet(methodFromCucumberLibraryTemplate, step, factory)
        } catch (e: Exception) {
            methodFromCucumberLibraryTemplate
        }
    }

    companion object {

        private const val STEP_DEFINITION_SUFFIX = "MyStepdefs"
        private const val FILE_TEMPLATE_CUCUMBER_JAVA_STEP_DEFINITION_JAVA = "Cucumber Java Step Definition.java"
        private const val DEFAULT_STEP_KEYWORD = "Given"
        private val LOG = Logger.getInstance(
            TzJavaStepDefinitionCreator::class.java
        )

        fun processGeneratedStepDefinition(stepDefinition: String, context: PsiElement): String {
            return stepDefinition.replace("PendingException", CucumberJavaUtil.getCucumberPendingExceptionFqn(context))
        }

        private fun replaceRegexpWithCucumberExpression(snippet: String, step: String): String {
            try {
                val registry = ParameterTypeRegistry(Locale.getDefault())
                val generator = CucumberExpressionGenerator(registry)
                val result = generator.generateExpressions(step)[0]
                if (result != null) {
                    val cucumberExpression = JavaSnippet().escapePattern(result.source)
                    val lines = snippet.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val start = lines[0].indexOf('(') + 1
                    lines[0] = lines[0].substring(0, start + 1) + cucumberExpression + "\")"
                    return StringUtil.join(lines, "")
                }
            } catch (ignored: Exception) {
                LOG.warn("Failed to replace regex with Cucumber Expression for step: $step")
            }
            return snippet
        }

        private fun createStepDefinitionFromSnippet(
            methodFromSnippet: PsiMethod, step: GherkinStep,
            factory: JVMElementFactory
        ): PsiMethod {
            val annotationsFromSnippetMethod = CucumberJavaUtil.getCucumberStepAnnotations(methodFromSnippet)
            val cucumberStepAnnotation = annotationsFromSnippetMethod[0]
            val regexp = CucumberJavaUtil.getPatternFromStepDefinition(cucumberStepAnnotation)
            var stepAnnotationName = cucumberStepAnnotation.qualifiedName
            if (stepAnnotationName == null) {
                stepAnnotationName = DEFAULT_STEP_KEYWORD
            }
            val fileTemplateDescriptor = FileTemplateDescriptor(FILE_TEMPLATE_CUCUMBER_JAVA_STEP_DEFINITION_JAVA)
            val fileTemplate =
                FileTemplateManager.getInstance(step.project).getCodeTemplate(fileTemplateDescriptor.fileName)
            var text = fileTemplate.text
            text = text.replace("\${STEP_KEYWORD}", stepAnnotationName).replace("\${STEP_REGEXP}", "\"" + regexp + "\"")
                .replace("\${METHOD_NAME}", methodFromSnippet.name)
                .replace("\${PARAMETERS}", methodFromSnippet.parameterList.text).replace("\${BODY}\n", "")
            text = processGeneratedStepDefinition(text, methodFromSnippet)
            return factory.createMethodFromText(text, step)
        }
    }

    open fun createStep(step: GherkinStep): Step {
        return Step(ArrayList(), step.keyword.text, step.name, 0, null, null)
    }
}