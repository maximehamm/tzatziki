package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.testFramework.writeChild
import io.nimbly.tzatziki.pdf.*
import io.nimbly.tzatziki.psi.getModule
import io.nimbly.tzatziki.util.peek
import io.nimbly.tzatziki.util.pop
import io.nimbly.tzatziki.util.push
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.impl.*
import java.io.ByteArrayOutputStream

class TzExportAction : TzAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {

        //TODO : Ma,age Cucumber "Rule" sections
        //TODO : Traduire les mots clef au moment d'editer
        //TODO : Manage tril quote ?
        //TODO : Manage comments

        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        if (file !is GherkinFile) return
        val module = file.getModule() ?: return
        val outputDirectory = CompilerPaths.getModuleOutputDirectory(module, true) ?: return

        // Prepare pdf generator
        val generator = PdfBuilder(
            PdfStyle(
                bottomCenter = file.name,
                bodyFontSize = "25px"),
            TableOfContents())

        // Customize styles
        generator.style.contentStyle = //language=CSS
            """
                * { font-size: 16px; margin: 0 0 0 0 }
                p { margin: 0 }
                
                h1 { font-size: 24px; margin-bottom: 10px; } 
                h2 { font-size: 20px; border-bottom: 5px; } 
                
                table { margin-top: 10px; max-width: 100%; margin-left: 15px;}
                table, th, td {  
                    font-size: 14px; 
                    vertical-align: top; 
                    border: 1px solid midnightblue;  
                    border-collapse: collapse;  
                }  
                th, td { padding: 5px;  white-space: nowrap; }
                th { color: chocolate }
                
                div { display: inline-block; }
                
                .feature { margin-left: 5px; }
                .featureHeader { margin-left: 5px; font-weight: bolder }
                .scenario, .scenarioOutline { margin-left: 15px; }
                .step { margin-left: 20px; }
                .stepParameter { color: chocolate; font-weight: bolder }
                .examples { margin-left: 20px; }
                
                .stepKeyword { color: grey; }
                
                """.trimIndent()

        // Build as Html
        file.accept(TzatizkiVisitor(generator))

        // Generate Pdf
        val output = ByteArrayOutputStream()
        buildPdf(generator, output)

        // Create file and open it
        val newFile = outputDirectory.writeChild("${file.name}.pdf", output.toByteArray())
        OpenFileDescriptor(project, newFile).navigate(true)
    }

    override fun update(event: AnActionEvent) {
        super.update(event)
        if (event.presentation.isVisible) {
            event.presentation.isEnabled = true
        }
    }

    override fun isDumbAware() = true

    private class TzatizkiVisitor(val generator: PdfBuilder): GherkinElementVisitor(), PsiRecursiveVisitor {

        private val stackTags = mutableListOf<String>()
        private val context = mutableListOf<PsiElement>()

        override fun visitWhiteSpace(space: PsiWhiteSpace) {
            if (!context.isTable()) {
                if (space.text.startsWith("\n") && stackTags.peek()?.startsWith("h") == true) {
                    closeTag()
                }
                append(space.text)
            }
            super.visitElement(space)
        }

        override fun visitFeature(feature: GherkinFeature) {
            openParagrah("feature")
            openTag("h1")
            super.visitFeature(feature)
            generator.breakPage()
            closeParagraph()
        }

        override fun visitFeatureHeader(header: GherkinFeatureHeaderImpl?) {
            openParagrah("featureHeader")
            super.visitFeatureHeader(header)
            closeParagraph()
        }

        override fun visitScenarioOutline(outline: GherkinScenarioOutline?) {
            openParagrah("scenarioOutline")
            openTag("h2")
            super.visitScenarioOutline(outline)
            closeParagraph()
        }

        override fun visitScenario(scenario: GherkinScenario?) {
            generator.paragraphStarts()
            openParagrah("scenario")
            openTag("h2")
            super.visitScenario(scenario)
            closeParagraph()
            generator.paragraphEnds()
        }

        override fun visitStep(step: GherkinStep?) {
            openParagrah("step")
            super.visitStep(step)
            closeParagraph()
        }

        override fun visitStepParameter(gherkinStepParameter: GherkinStepParameterImpl?) {
            openClass("stepParameter")
            super.visitStepParameter(gherkinStepParameter)
            closeClass()
        }

        override fun visitExamplesBlock(block: GherkinExamplesBlockImpl?) {
            generator.paragraphStarts()
            openParagrah("examples")
            super.visitExamplesBlock(block)
            closeParagraph()
            generator.paragraphEnds()
        }

        override fun visitTable(table: GherkinTableImpl) {
            generator.paragraphStarts()
            openTag("table")
            super.visitTable(table)
            closeTag()
            generator.paragraphEnds()
        }

        override fun visitTableHeaderRow(row: GherkinTableHeaderRowImpl) {
            openTag("tr")
            super.visitTableHeaderRow(row)
            closeTag()
        }

        override fun visitTableRow(row: GherkinTableRowImpl) {
            openTag("tr")
            super.visitTableRow(row)
            closeTag()
        }

        override fun visitGherkinTableCell(cell: GherkinTableCell) {
            openTag(if (context.isHeader()) "th" else "td")
            super.visitGherkinTableCell(cell)
            closeTag()
        }

        override fun visitElement(element: PsiElement) {

            ProgressIndicatorProvider.checkCanceled()

            fun append() {
                if (element !is LeafPsiElement) return
                if (context.isRow() && element.text == "|") return
                if (element.elementType == GherkinTokenTypes.STEP_PARAMETER_BRACE) return

                if (element.elementType == GherkinTokenTypes.STEP_KEYWORD) {
                    openClass("stepKeyword")
                    append(element.text)
                    closeClass()
                }
                else {
                    append(element.text)
                }
            }

            append()
            context.push(element)
            element.acceptChildren(this)
            context.pop()
        }

        private fun openTag(tag: String): TzatizkiVisitor {
            generator.append("<$tag>")
            stackTags.push(tag)
            return this
        }

        private fun openClass(clazz: String): TzatizkiVisitor {
            generator.append("<div class='$clazz'>")
            stackTags.push("div")
            return this
        }

        private fun openParagrah(clazz: String): TzatizkiVisitor {
            generator.append("<p class='$clazz'>")
            stackTags.push("p")
            return this
        }

        private fun closeTag(): TzatizkiVisitor {
            generator.append("</${stackTags.pop()}>")
            return this
        }

        private fun closeParagraph() = closeTag()

        private fun closeClass() = closeTag()

        private fun append(string: String): TzatizkiVisitor {
            generator.append(string.escape())
            return this
        }
    }
}

private fun <E> MutableList<E>.isTable() = peek() is GherkinTable
private fun <E> MutableList<E>.isHeader() = peek() is GherkinTableHeaderRowImpl
private fun <E> MutableList<E>.isCell() = peek() is GherkinTableCell
private fun <E> MutableList<E>.isRow() = peek() is GherkinTableRow