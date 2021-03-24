package io.nimbly.tzatziki.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.testFramework.writeChild
import io.nimbly.tzatziki.config.loadConfig
import io.nimbly.tzatziki.pdf.*
import io.nimbly.tzatziki.psi.getFile
import io.nimbly.tzatziki.psi.getModule
import io.nimbly.tzatziki.psi.loadStepParams
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes.*
import org.jetbrains.plugins.cucumber.psi.impl.*
import java.io.ByteArrayOutputStream

class TzExportAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {

        //TODO : Add a front page with template
        //TODO : La première page ne doit pas avoir de footer / header
        //TODO : Cutomiser le titre du sommaire : "Table of contents"

        //TODO : Numéroter / renuméroter les features
        //TODO : Imprimer les features dans l'ordre de la numérotation
        //TODO : Choisir et ordonner les features à exporter en PDF ?

        //TODO Later : Set a parameter to decide whether or not to print the comments
        //TODO Later : Traduire les mots clef au moment d'editer
        //TODO Later : Renuméroter les scénario comme le plugin bidule

        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val vfiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (vfiles.isNullOrEmpty()) return

        try {
            exportFeatures(vfiles.toList(), project)
        } catch (e: TzatzikiException) {
            UpdateChecker.getNotificationGroup().createNotification(
                "Cucumber+", e.message ?: "Cucumber+ error !",
                NotificationType.INFORMATION).notify(project)

        }
    }

    private fun exportFeatures(paths: List<VirtualFile>, project: Project) {

        // Find all relative gherkin files
        val files = loadGherkinFiles(paths, project)
        if (files.isEmpty())
            throw TzatzikiException("No Cucumber feature found !")

        // Get project root
        val module = files.first().getModule() ?: return
        val outputDirectory = CompilerPaths.getModuleOutputDirectory(module, true) ?: return

        // Load config
        val config = loadConfig(paths, project)

        // Prepare pdf generator
        val pdfStyle = config.buildStyles()
        val generator = PdfBuilder(pdfStyle)

        // Summary and front page
        if (files.size > 1) {

            // Front page
            initFreeMarker().apply {
                registerTemplates("EXPORT" to config.frontpage)
                generator.append("EXPORT", this, "config" to config, "logo" to config.picture)
            }
            generator.breakPage()

            // Summary
            generator.append("<br/><h3>Table of contents :</h3><br/><br/>")
            generator.insertSummary()
        }

        // Content
        generator.breakPage()

        // Build as Html
        val visitor = TzatizkiVisitor(generator)
        files.forEach {
            it.accept(visitor)
        }
        visitor.closeAllTags()

        // Generate Pdf
        val output = ByteArrayOutputStream()
        buildPdf(generator, output)

        // Create file and open it
        val fileName = if (files.size == 1) files.first().name else "cucumber"
        val newFile = outputDirectory.writeChild("${fileName}.pdf", output.toByteArray())
        OpenFileDescriptor(project, newFile).navigate(true)
    }

    private fun loadGherkinFiles(paths: List<VirtualFile>, project: Project): List<GherkinFile> {

        fun VirtualFile.allGherkinFiles(all: MutableList<GherkinFile> = mutableListOf()): List<GherkinFile> {
            children.forEach {
                if (it.isDirectory) {
                    it.allGherkinFiles(all)
                }
                else {
                    val file = it.getFile(project)
                    if (file is GherkinFile)
                        all.add(file)
                }
            }
            return all
        }

        val list = mutableListOf<GherkinFile>()
        paths.forEach {
            if (it.isDirectory) {
                list.addAll(it.allGherkinFiles())
            }
            else {
                val f = it.getFile(project)
                if (f is GherkinFile)
                    list.add(f)
            }
        }
        return list
    }

    override fun update(event: AnActionEvent) {

        val file = event.getData(CommonDataKeys.PSI_FILE)
        val project = event.getData(CommonDataKeys.PROJECT)
        val isGherkinFile = file?.fileType == GherkinFileType.INSTANCE

        var isVisible = isGherkinFile || file == null

        if (isVisible && project!=null) {

            // Check selected files all bellong to same root
            var root: VirtualFile? = null
            event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.find {
                val r = ProjectFileIndex.SERVICE.getInstance(project).getSourceRootForFile(it)
                if (r == null || root!=null && r!=root) {
                    isVisible = false
                    true
                }
                else {
                    root =r
                    false
                }
            }
        }

        event.presentation.isEnabledAndVisible = isVisible
        event.presentation.text = "Export feature${if (isGherkinFile) "" else "s"} to PDF"
        super.update(event)
    }

    override fun isDumbAware()
        = true

    private class TzatizkiVisitor(val generator: PdfBuilder) : GherkinElementVisitor(), PsiRecursiveVisitor {

        private val stackTags = mutableListOf<String>()
        private val context = mutableListOf<PsiElement>()
        private var stepParams: List<TextRange>? = null

        override fun visitElement(elt: PsiElement) {

            ProgressIndicatorProvider.checkCanceled()

            fun append() {
                if (elt !is LeafPsiElement) return
                if (context.isRow() && elt.text == "|") return
                if (elt.elementType == STEP_PARAMETER_BRACE) return
                if (elt.elementType == PYSTRING) return

                if (elt.elementType == FEATURE_KEYWORD || context.isFeature())
                    return span("featureTitle") { append(elt.text) }

                if (elt.elementType == RULE_KEYWORD || context.isRule())
                    return span("ruleTitle") { append(elt.text) }

                if (elt.elementType == SCENARIO_KEYWORD || elt.elementType == SCENARIO_OUTLINE_KEYWORD || context.isScenario())
                    return span("scenarioTitle") { append(elt.text) }

                if (elt.elementType == STEP_KEYWORD)
                    return span("stepKeyword") { append(elt.text) }

                if (context.isStep() && stepParams?.isNotEmpty() == true) {

                    val shiftedSlices = stepParams!!
                        .filter { it.startOffset >= elt.startOffset }
                        .map { it.shiftLeft(elt.startOffset) }

                    elt.text.shopUp(shiftedSlices)
                        .forEach {
                            if (it.isInterRange)
                                append(it.text)
                            else
                                span("stepParameter") { append(it.text) }
                        }
                    return
                }

                val text = if (elt.elementType == PYSTRING_TEXT) elt.text.trimIndent() else elt.text

                append(text)
            }

            append()

            context.push(elt)
            elt.acceptChildren(this)
            context.pop()
        }

        override fun visitWhiteSpace(space: PsiWhiteSpace) {
            if (!context.isTable()) {
                append(space.text)
            }
            super.visitElement(space)
        }

        override fun visitFeature(feature: GherkinFeature) {

            addSummaryEntry(1,  feature.featureName)

            p("feature") { super.visitFeature(feature) }

            if (feature != (feature.parent as GherkinFile).features.last())
                generator.breakPage()
        }

        override fun visitFeatureHeader(header: GherkinFeatureHeaderImpl) =
            p("featureHeader") { super.visitFeatureHeader(header) }

        override fun visitRule(rule: GherkinRule) {
            addSummaryEntry(2, rule.ruleName)
            p("rule") { super.visitRule(rule) }
        }

        override fun visitScenarioOutline(scenario: GherkinScenarioOutline) {
            addSummaryEntry(2, scenario.scenarioName)
            nobreak { p("scenario") { super.visitScenarioOutline(scenario) } }
        }

        override fun visitScenario(scenario: GherkinScenario) {
            addSummaryEntry(2, scenario.scenarioName)
            nobreak { p("scenario") { super.visitScenario(scenario) } }
        }

        override fun visitStep(step: GherkinStep) = p("step") {
            stepParams = loadStepParams(step)
            super.visitStep(step)
            stepParams = null
        }

        override fun visitStepParameter(gherkinStepParameter: GherkinStepParameterImpl?) =
            span("stepParameter") { super.visitStepParameter(gherkinStepParameter) }

        override fun visitExamplesBlock(block: GherkinExamplesBlockImpl) =
            nobreak { p("examples") { super.visitExamplesBlock(block) } }

        override fun visitTable(table: GherkinTableImpl) = nobreak { tag("table") { super.visitTable(table) } }

        override fun visitTableHeaderRow(row: GherkinTableHeaderRowImpl) = tag("tr") { super.visitTableHeaderRow(row) }

        override fun visitTableRow(row: GherkinTableRowImpl) = tag("tr") { super.visitTableRow(row) }

        override fun visitGherkinTableCell(cell: GherkinTableCell) = tag(if (context.isHeader()) "th" else "td") {
            tag("span") {
                super.visitGherkinTableCell(cell)
            }
        }

        override fun visitPystring(phstring: GherkinPystring?) = nobreak {
            div("docstringMargin") {
                p("docstring") {
                    super.visitPystring(phstring)
                }
            }
        }

        override fun visitComment(comment: PsiComment) {
            //Do not export comments
        }

        private fun append(string: String): TzatizkiVisitor {
            generator.append(string.escape())
            return this
        }

        private fun span(clazz: String, function: () -> Unit) {
            generator.append("<span class='$clazz'>")
            stackTags.push("span")
            function()
            close("span")
        }

        fun tag(tag: String, function: () -> Unit) {
            generator.append("<$tag>")
            stackTags.push(tag)
            function()
            close(tag)
        }

        fun p(clazz: String, function: () -> Unit) {
            generator.append("<p class='$clazz'>")
            stackTags.push("p")
            function()
            close("p")
        }

        fun div(clazz: String, function: () -> Unit) {
            generator.append("<div class='$clazz'>")
            stackTags.push("div")
            function()
            close("div")
        }

        private fun nobreak(function: () -> Unit) {
            generator.paragraphStarts()
            function()
            generator.paragraphEnds()
        }

        private fun close(tag: String? = null): TzatizkiVisitor {
            val pop = stackTags.pop()
            if (tag != null && tag != pop)
                throw Exception("HTML tag malformed!")
            generator.append("</$pop>")
            return this
        }

        fun closeAllTags() {
            while (stackTags.isNotEmpty()) {
                close()
            }
        }

        private fun addSummaryEntry(level: Int, title: String) {
            if (title.isNotBlank())
                generator.addSummaryEntry(level, title.escape())
        }
    }
}

private fun <E> MutableList<E>.isFeature() = peek() is GherkinFeature
private fun <E> MutableList<E>.isRule() = peek() is GherkinRule
private fun <E> MutableList<E>.isScenario() = peek() is GherkinScenario || peek() is GherkinScenarioOutline
private fun <E> MutableList<E>.isStep() = peek() is GherkinStep

private fun <E> MutableList<E>.isTable() = peek() is GherkinTable
private fun <E> MutableList<E>.isHeader() = peek() is GherkinTableHeaderRowImpl
private fun <E> MutableList<E>.isCell() = peek() is GherkinTableCell
private fun <E> MutableList<E>.isRow() = peek() is GherkinTableRow
