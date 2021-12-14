/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki.actions

import com.github.rjeschke.txtmark.Processor
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages.*
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.ActionIcons.CUCUMBER_PLUS
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.config.loadConfig
import io.nimbly.tzatziki.markdown.adaptPicturesPath
import io.nimbly.tzatziki.pdf.*
import io.nimbly.tzatziki.psi.checkExpression
import io.nimbly.tzatziki.psi.loadStepParams
import io.nimbly.tzatziki.settings.CucumberPersistenceState
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes.*
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider
import org.jetbrains.plugins.cucumber.psi.impl.*
import java.io.ByteArrayOutputStream

class TzExportAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {

        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val vfiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (vfiles.isNullOrEmpty()) return

        try {
            exportFeatures(vfiles.toList(), project)
        } catch (e: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotification("Please wait until index is ready")
        } catch (e: TzatzikiException) {
            project.notification(e.message ?: "$TZATZIKI_NAME error !", NotificationType.WARNING)
        } catch (e: Exception) {
            e.printStackTrace()
            project.notification(e.message ?: "$TZATZIKI_NAME error !", NotificationType.WARNING)
        }
    }

    private fun exportFeatures(paths: List<VirtualFile>, project: Project) {

        // Find all relative gherkin files
        val allFiles = loadGherkinFiles(paths, project)
        if (allFiles.isEmpty())
            throw TzatzikiException("No Cucumber feature found !")

        // Filter per tags
        val tagExpression = ServiceManager.getService(project, CucumberPersistenceState::class.java).tagExpression()
        val files = allFiles.filter { it.checkExpression(tagExpression) }
        if (files.isEmpty())
            throw TzatzikiException("No Cucumber feature found having scenarios matching selected tags !")

        // Get project root
        val tempDir = FileUtilRt.createTempDirectory("cucumber+", null, true)
        val outputDirectory = LocalFileSystem.getInstance().findFileByIoFile(tempDir)
            ?: throw TzatzikiException("Unable to create temporary file")

        // Load config
        val config = loadConfig(paths, project)
        val pdfStyle = config.buildStyles()

        // Ask to use landscape or not
        val selected = showYesNoCancelDialog(project,
            if (files.size == 1) "Exporting one feature to PDF" else "Exporting ${files.size} features to PDF",
            TZATZIKI_NAME,
            "&Cancel", "&Portrait", "&Lanscape", CUCUMBER_PLUS)
        val orientation = when (selected) {
            NO -> "portrait"
            CANCEL -> "landscape"
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
        val visitor = TzatizkiVisitor(generator, config.language, tagExpression)
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

    override fun update(event: AnActionEvent) {

        val file = event.getData(CommonDataKeys.PSI_FILE)
        val project = event.getData(CommonDataKeys.PROJECT)
        val vfiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val isGherkinFile = file?.fileType == GherkinFileType.INSTANCE

        var isVisible = vfiles!=null && (isGherkinFile || file == null)

        if (isVisible && project!=null) {

            // Check selected files all bellong to same root
            var root: VirtualFile? = null
            vfiles?.find {
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

    private class TzatizkiVisitor(val generator: PdfBuilder, val dialect: String, val tagExpression: Expression?) : GherkinElementVisitor(), PsiRecursiveVisitor {

        private val stackTags = mutableListOf<String>()
        private val context = mutableListOf<PsiElement>()
        private var stepParams: List<TextRange>? = null
        private var summaryLevel = 1

        override fun visitElement(elt: PsiElement) {

            ProgressIndicatorProvider.checkCanceled()

            fun append() {
                if (elt !is LeafPsiElement) return
                if (context.isRow() && elt.text == "|") return
                if (elt.elementType == STEP_PARAMETER_BRACE) return
                if (elt.elementType == PYSTRING) return

                if (elt.elementType == FEATURE_KEYWORD || context.isFeature())
                    return span("featureTitle") { append(elt.translate()) }

                if (elt.elementType == RULE_KEYWORD || context.isRule())
                    return span("ruleTitle") { append(elt.translate()) }

                if (elt.elementType == SCENARIO_KEYWORD || elt.elementType == SCENARIO_OUTLINE_KEYWORD || context.isScenario())
                    return span("scenarioTitle") { append(elt.translate()) }

                if (elt.elementType == STEP_KEYWORD)
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
        if (name.isNotBlank()) name else this.firstChild.text
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
