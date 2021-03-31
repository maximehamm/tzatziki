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

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestFrameworkRunningModel
import com.intellij.execution.testframework.export.ExportTestResultsConfiguration
import com.intellij.execution.testframework.export.ExportTestResultsConfiguration.ExportFormat
import com.intellij.execution.testframework.export.TestResultsXmlFormatter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.TransformerException
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.sax.TransformerHandler
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class TzCaptureTestResultsAction : AnAction() {

    private val myToolWindowId: String = "Run"
    private val myRunConfiguration: RunConfiguration? = null
    private var myModel: TestFrameworkRunningModel? = null

    override fun actionPerformed(e: AnActionEvent) {

        val c = ExportTestResultsConfiguration()

        c.outputFolder
        c.userTemplatePath
        c.isOpenResults

        c.exportFormat = ExportFormat.Xml
        c.isOpenResults = false
        val xml = getOutputText(c)


    }

    @Throws(IOException::class, TransformerException::class, SAXException::class)
    private fun getOutputText(config: ExportTestResultsConfiguration): String? {
        if (myRunConfiguration == null || myModel == null)
            return null

        val transformerFactory = SAXTransformerFactory.newInstance() as SAXTransformerFactory
        var handler: TransformerHandler? = null
        if (config.exportFormat == ExportFormat.Xml) {
            handler = transformerFactory.newTransformerHandler()
            handler.transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            handler.transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4") // NON-NLS
        }
        else {

            var xslSource: Source? = null
            if (config.exportFormat == ExportFormat.BundledTemplate) {
                javaClass.getResourceAsStream("intellij-export.xsl").use { bundledXsltUrl ->
                    xslSource = StreamSource(bundledXsltUrl)
                }
            } else {
                val xslFile = File(config.userTemplatePath)
                if (!xslFile.isFile) {
                    showBalloon(
                        myRunConfiguration.project,
                        ExecutionBundle.message("export.test.results.custom.template.not.found", xslFile.path)
                    )
                    return null
                }
                xslSource = StreamSource(xslFile)
            }

            if (xslSource != null) {
                handler = transformerFactory.newTransformerHandler(xslSource)
                handler.transformer.setParameter(
                    "TITLE", ExecutionBundle.message(
                        "export.test.results.filename", myRunConfiguration.name,
                        myRunConfiguration.type.displayName
                    )
                )
            }
        }

        if (handler!=null) {
            val w = StringWriter()
            handler.setResult(StreamResult(w))
            try {
                TestResultsXmlFormatter.execute(myModel!!.root, myRunConfiguration, myModel!!.getProperties(), handler)
            } catch (e: ProcessCanceledException) {
                return null
            }
            return w.toString()
        }

        return null
    }

    fun setModel(model: TestFrameworkRunningModel) {
        myModel = model
    }

    private fun showBalloon(
        project: Project,
        text: String ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            if (ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId) != null) {
                ToolWindowManager.getInstance(project).notifyByBalloon(myToolWindowId, MessageType.ERROR, text, null, null)
            }
        }
    }

    override fun update(event: AnActionEvent) {

        val file = event.getData(CommonDataKeys.PSI_FILE)
        val vfiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val isGherkinFile = file?.fileType == GherkinFileType.INSTANCE

        val isVisible = vfiles!=null && (isGherkinFile || file == null)

        event.presentation.isEnabledAndVisible = isVisible
        super.update(event)
    }
}