package io.nimbly.tzatziki.pdf

import com.github.rjeschke.txtmark.Processor
import icons.ActionIcons
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.config.loadConfig
import io.nimbly.tzatziki.markdown.adaptPicturesPath
import io.nimbly.tzatziki.services.tzFileService
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import io.nimbly.tzatziki.util.TzatzikiException
import io.nimbly.tzatziki.util.checkExpression
import io.nimbly.tzatziki.util.chooseFileName
import io.nimbly.tzatziki.util.getFile
import io.nimbly.tzatziki.util.loadStepParams
import io.nimbly.tzatziki.util.peek
import io.nimbly.tzatziki.util.pop
import io.nimbly.tzatziki.util.push
import io.nimbly.tzatziki.util.shopUp
import org.jetbrains.plugins.cucumber.psi.GherkinElementType
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinPsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinPystring
import org.jetbrains.plugins.cucumber.psi.GherkinRule
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider
import org.jetbrains.plugins.cucumber.psi.impl.GherkinExamplesBlockImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFeatureHeaderImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepParameterImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableRowImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import java.io.ByteArrayOutputStream

class ExportPdf(private val paths: List<VirtualFile>, val project: Project) {

    fun exportFeatures() {

        // Check filtering
        val filterExpression: Expression? = project.tzFileService().let {
            if (it.filterByTags) it.tagExpression() else null
        }

        // Find all relative gherkin files
        val allFiles = loadGherkinFiles(paths, project)
        if (allFiles.isEmpty())
            throw TzatzikiException("No Cucumber feature found !")

        // Filter per tags
        val files: List<GherkinFile>
        if (filterExpression != null) {
            files = allFiles.filter { it.checkExpression(filterExpression) }
            if (files.isEmpty())
                throw TzatzikiException("No Cucumber feature found matching selected tags !")
        }
        else {
            files = allFiles
        }

        // Get project root
        val tempDir = FileUtilRt.createTempDirectory("Cucumber+", null, true)
        val outputDirectory = LocalFileSystem.getInstance().findFileByIoFile(tempDir)
            ?: throw TzatzikiException("Unable to create temporary file")

        // Load config
        val config = loadConfig(paths, project)
        val pdfStyle = config.buildStyles()

        // Ask to use landscape or not
        val selected = Messages.showYesNoCancelDialog(
            project,
            if (files.size == 1) "Exporting one feature to PDF" else "Exporting ${files.size} features to PDF",
            TZATZIKI_NAME,
            "&Cancel", "&Portrait", "&Landscape", ActionIcons.CUCUMBER_PLUS_64
        )
        val orientation = when (selected) {
            Messages.NO -> "portrait"
            Messages.CANCEL -> "landscape"
            else -> return
        }
        pdfStyle.orientation = orientation
        pdfStyle.first?.orientation = orientation

        // Prepare pdf generator
        val generator = PdfBuilder(pdfStyle)

        // Summary and front page
        if (files.size > 1) {

            // Front page
            initFreeMarker(PictureWrapper).apply {
                registerTemplates("EXPORT" to config.template)
                generator.append("EXPORT", this,
                    "frontpage" to config.frontpage,
                    "logo" to config.picture,
                    "orientation" to orientation)
            }
            generator.breakPage()

            // Summary
            generator.append(config.summaryTitle)
            generator.insertSummary()
        }

        // Content
        generator.breakPage()

        // Build as Html
        val visitor = TzatizkiVisitor(generator, filterExpression)
        files.forEach {
            it.accept(visitor)
            if (it != files.last())
                generator.breakPage()
        }
        visitor.closeAllTags()

        // Generate Pdf
        val output = ByteArrayOutputStream()
        buildPdf(generator, output)

        // Create file and open it
        val name = if (files.size == 1) files.first().name else "cucumber"
        val fileName = outputDirectory.chooseFileName(name, "pdf")

        ApplicationManager.getApplication().runWriteAction {

            val newFile = outputDirectory.createChildData(this, fileName)
            newFile.setBinaryContent(output.toByteArray())

            OpenFileDescriptor(project, newFile).navigate(true)
        }
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

        return list.sortedBy { it.name }
    }

    private class TzatizkiVisitor(val generator: PdfBuilder, val tagExpression: Expression?) : GherkinElementVisitor(),
        PsiRecursiveVisitor {

        private val stackTags = mutableListOf<String>()
        private val context = mutableListOf<PsiElement>()
        private var stepParams: List<TextRange>? = null
        private var summaryLevel = 1

        override fun visitElement(elt: PsiElement) {

            ProgressIndicatorProvider.checkCanceled()

            fun append() {
                if (elt !is LeafPsiElement) return
                if (context.isRow() && elt.text == "|") return
                if (elt.elementType == GherkinTokenTypes.STEP_PARAMETER_BRACE) return
                if (elt.elementType == GherkinTokenTypes.PYSTRING) return

                if (elt.elementType == GherkinTokenTypes.FEATURE_KEYWORD || context.isFeature())
                    return span("featureTitle") { append(elt.translate()) }

                if (elt.elementType == GherkinTokenTypes.RULE_KEYWORD || context.isRule())
                    return span("ruleTitle") { append(elt.translate()) }

                if (elt.elementType == GherkinTokenTypes.SCENARIO_KEYWORD || elt.elementType == GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD || context.isScenario())
                    return span("scenarioTitle") { append(elt.translate()) }

                if (elt.elementType == GherkinTokenTypes.STEP_KEYWORD)
                    return span("stepKeyword") { append(elt.translate()) }

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

                val text = if (elt.elementType == GherkinTokenTypes.PYSTRING_TEXT) elt.text.trimIndent() else elt.text

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
            if (space.parent is GherkinFeature
                && space.nextSibling is GherkinStepsHolder
                && space.text.count { it == '\n' } < 2
            ) {
                append("\n")
            } else if (space.parent is GherkinStepsHolder
                && space.nextSibling is GherkinStep
                && space.prevSibling !is GherkinStep
                && space.text.count { it == '\n' } < 2
            ) {
                append("\n")
            }
            super.visitElement(space)
        }

        override fun visitFeature(feature: GherkinFeature) {
            summary(feature) {
                nobreak { p("feature") { super.visitFeature(feature) } }
            }
            if (feature != (feature.parent as GherkinFile).features.last())
                generator.breakPage()
        }

        override fun visitFeatureHeader(header: GherkinFeatureHeaderImpl) =
            p("featureHeader") {
                appendMarkdown(header.text, header.containingFile)
                super.visitElement(header)
            }

        override fun visitRule(rule: GherkinRule) {
            summary(rule) {
                nobreak {
                    p("rule") { super.visitRule(rule) }
                }
            }
        }

        override fun visitScenarioOutline(scenario: GherkinScenarioOutline) {
            if (!scenario.checkExpression(tagExpression)) {
                return super.visitElement(scenario)
            }
            summary(scenario) {
                nobreak {
                    p("scenario") { super.visitScenarioOutline(scenario) }
                }
            }
        }

        override fun visitScenario(scenario: GherkinScenario) {
            if (!scenario.checkExpression(tagExpression)) {
                return super.visitElement(scenario)
            }
            summary(scenario) {
                nobreak {
                    p("scenario") { super.visitScenario(scenario) }
                }
            }
        }

        override fun visitStep(step: GherkinStep) = p("step") {
            stepParams = loadStepParams(step)
            super.visitStep(step)
            stepParams = null
        }

        override fun visitStepParameter(gherkinStepParameter: GherkinStepParameterImpl?) =
            span("stepParameter") { super.visitStepParameter(gherkinStepParameter) }

        override fun visitExamplesBlock(block: GherkinExamplesBlockImpl) =
            nobreak {
                p("examples") { super.visitExamplesBlock(block) }
            }

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
                p("docstring") { super.visitPystring(phstring) }
            }
        }

        override fun visitComment(comment: PsiComment) {
            //Do not export comments
        }

        private fun append(string: String): TzatizkiVisitor {
            generator.append(string.escape())
            return this
        }

        private fun appendMarkdown(string: String, file: PsiFile): TzatizkiVisitor {
            try {

                // Add CR
                val str = string.replace("\n", "<br/>")

                // Markdown to html
                val html1 = Processor.process(str)

                // Replace reference to file by absolute path to it
                val html2 = html1.adaptPicturesPath(file)

                // Add html
                generator.append(html2)
            } catch (e: Exception) {
                append(string)
            }
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

        private fun summary(element: GherkinPsiElement, function: () -> Unit) {
            val title = element.name()
            generator.addSummaryEntry(summaryLevel++, title.escape())
            function()
            summaryLevel--
        }


        fun LeafPsiElement.translate() = text // translate(this, text, dialect)
    }
}

private fun GherkinPsiElement.name(neverBlank: Boolean = true): String {

    val name = when {
        this is GherkinStepsHolder -> this.scenarioName
        this is GherkinRule -> this.ruleName
        this is GherkinFeature -> this.featureName
        else -> this.firstChild.text
    }

    return if (neverBlank)
        name.ifBlank { this.firstChild.text }
    else
        name
}

private fun translate(element: LeafPsiElement, text: String, dialect: String): String {

    val keyword = JsonGherkinKeywordProvider
        .getKeywordProvider(true)
        .getKeywordsTable(dialect)
        .getKeywords(element.elementType as GherkinElementType)
        ?.filter { it != "*" }
        ?.map { it.length to it }
        ?.minByOrNull { it.first }
        ?.second

    return keyword ?: text
}

private fun <E> MutableList<E>.isFeature() = peek() is GherkinFeature
private fun <E> MutableList<E>.isRule() = peek() is GherkinRule
private fun <E> MutableList<E>.isScenario() = peek() is GherkinScenario || peek() is GherkinScenarioOutline
private fun <E> MutableList<E>.isStep() = peek() is GherkinStep

private fun <E> MutableList<E>.isTable() = peek() is GherkinTable
private fun <E> MutableList<E>.isHeader() = peek() is GherkinTableHeaderRowImpl
private fun <E> MutableList<E>.isCell() = peek() is GherkinTableCell
private fun <E> MutableList<E>.isRow() = peek() is GherkinTableRow
