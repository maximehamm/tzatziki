/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

package io.nimbly.tzatziki.config

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import io.nimbly.tzatziki.pdf.ELeader
import io.nimbly.tzatziki.pdf.ESummaryDepth
import io.nimbly.tzatziki.pdf.PdfStyle
import io.nimbly.tzatziki.pdf.Picture
import io.nimbly.tzatziki.util.*
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.Path


const val CONFIG_FOLDER = ".cucumber+"

const val PROPERTIES_FILENAME = "cucumber+.properties"
const val PROPERTIES_DEFAULT_FILENAME = "cucumber+.default.properties"

const val CSS_FILENAME = "cucumber+.css"
const val CSS_DEFAULT_FILENAME = "cucumber+.default.css"

const val EXPORT_PICTURE_FILENAME = "cucumber+.picture.svg"
const val EXPORT_PICTURE_DEFAULT_FILENAME = "cucumber+.picture.default.svg"

const val EXPORT_TEMPLATE_FILENAME = "cucumber+.template.ftl"
const val EXPORT_TEMPLATE_DEFAULT_FILENAME = "cucumber+.template.default.ftl"

const val FONT = "cucumber+.font.ttf"
const val FONT_DEFAULT = "cucumber+.font.default.ttf"

val ALL_DEFAULTS = listOf(
    PROPERTIES_DEFAULT_FILENAME, PROPERTIES_FILENAME, FONT_DEFAULT,
    CSS_DEFAULT_FILENAME, EXPORT_PICTURE_DEFAULT_FILENAME, EXPORT_TEMPLATE_DEFAULT_FILENAME)

fun loadConfig(path: VirtualFile, project: Project): Config {

    // Look for root config folder
    val root = ProjectFileIndex.getInstance(project).getSourceRootForFile(path)
    if (root == null) {

        var relativePath: String
        if (project.basePath != null)
            relativePath = Path(project.basePath!!).relativize(Path(path.path)).toString()
        else
            relativePath = path.path.substringAfterLast(project.name)

        if (relativePath.isBlank())
            relativePath = project.name + "/"

        throw TzatzikiException("Common parent path '${relativePath}' should be part of same resource path...")
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    var rootConfig = root.findChild(CONFIG_FOLDER)
    if (rootConfig == null) {

        rootConfig = root.copyDefaultsToFolder(path, project)
//        project.notification("Configuration <a href='PROP'>files</a> were created") {
//            PsiManager.getInstance(project).findDirectory(rootConfig)?.navigate(true)
//        }

        project.notificationAction("Configuration were created", NotificationType.INFORMATION,
            mapOf("Open" to {
                PsiManager.getInstance(project).findDirectory(rootConfig)?.navigate(true)
            })
        )
    }

    // Update default files
    rootConfig.updateDefaultFiles(project)

    // Select
    val propertiesFiles = root.loadAllProperties(path)
    val cssFile = root.loadCss(path)
    val picture = root.loadPicture(path)
    val frontpage = root.loadTemplate(path)
    val font = root.loadFont(path)

    // Return config
    return createConfiguration(propertiesFiles, cssFile, picture, frontpage, font!!)
}

fun loadConfig(files: List<VirtualFile>, project: Project): Config {

    if (files.size == 1)
        return loadConfig(files.first(), project)

    fun VirtualFile.stack(): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        var vf = parent
        while (vf != null) {
            list.add(vf)
            vf = vf.parent
        }
        return list.reversed()
    }

    fun List<*>.allEquals(): Boolean {
        val v = first()
        forEach {
            if (it != v) return false
        }
        return true
    }

    val stacks: List<Iterator<VirtualFile>> = files.map { it.stack().iterator() }

    var common: VirtualFile? = null
    while (true) {

        val nexts: List<VirtualFile> = stacks
            .map { if (it.hasNext()) it.next() else null  }
            .filterNotNull()

        if (nexts.size != stacks.size) break
        if (!nexts.allEquals()) break

        common = nexts.first()
    }

    if (common == null)
        throw TzatzikiException("Selected files does not belongs to same project... this is not supported !")

    return loadConfig(common, project)
}

private fun VirtualFile.copyDefaultsToFolder(path: VirtualFile, project: Project): VirtualFile {
    return WriteCommandAction.runWriteCommandAction<VirtualFile>(project) {
        val dir = createChildDirectory(path, CONFIG_FOLDER)
        ALL_DEFAULTS
            .forEach {
                if (dir.findChild(it) == null)
                    dir.addFrom(it)
            }
        return@runWriteCommandAction dir
    }
}

private fun VirtualFile.addFrom(file: String) {
    val bytes = getResource("/io/nimbly/tzatziki/config/$file")
    createChildData(this, file).setBinaryContent(bytes)
}

private fun VirtualFile.setContentFrom(file: String) {
    val bytes = getResource("/io/nimbly/tzatziki/config/$file")
    setBinaryContent(bytes)
}


private fun getResource(path: String)
    = {}.javaClass.getResourceAsStream(path).readAllBytes()

fun getFile(path: String)
        = File({}.javaClass.getResource(path)?.file)

private fun VirtualFile.updateDefaultFiles(project: Project) {
    ALL_DEFAULTS.forEach { fileName ->

        var currentFile = findChild(fileName)
        if (currentFile == null) {

            // Create file it not exist
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            WriteCommandAction.runWriteCommandAction(project) {
                currentFile = createChildData(this, fileName)
                currentFile!!.setContentFrom(fileName)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()

//            project.notification("Configuration <a href='PROP'>file</a> added") {
//                PsiManager.getInstance(project).findFile(currentFile!!)?.navigate(true)
//            }

            project.notificationAction("Configuration file added", NotificationType.INFORMATION,
                mapOf("Open file" to {
                    PsiManager.getInstance(project).findFile(currentFile!!)?.navigate(true)
                })
            )


        } else if (fileName != PROPERTIES_FILENAME) {

            // Override with new default
            val hash = {}.javaClass.getResourceAsStream("/io/nimbly/tzatziki/config/$fileName").readAllBytes()!!.contentHashCode()
            if (hash != currentFile!!.contentsToByteArray()!!.contentHashCode()) {

                PsiDocumentManager.getInstance(project).commitAllDocuments()
                WriteCommandAction.runWriteCommandAction(project) {
                    currentFile!!.setContentFrom(fileName)
                }
                PsiDocumentManager.getInstance(project).commitAllDocuments()

//                project.notification("Configuration <a href='PROP'>file</a> upated") {
//                    PsiManager.getInstance(project).findFile(currentFile!!)?.navigate(true)
//                }
                project.notificationAction("Configuration file upated", NotificationType.INFORMATION,
                    mapOf("Open file" to {
                        PsiManager.getInstance(project).findFile(currentFile!!)?.navigate(true)
                    })
                )
            }
        }
    }
}

private fun VirtualFile.loadAllProperties(file: VirtualFile): List<Properties> {

    val all = mutableListOf<Properties>()
    var vf: VirtualFile? = file
    while (vf != null) {

        val folder = vf.findChild(CONFIG_FOLDER)

        if (folder != null) {
            val config = folder.findChild(PROPERTIES_FILENAME)
            if (config != null) {
                val p = Properties()
                p.load(InputStreamReader(config.inputStream, Charset.forName("UTF-8")))
                all.add(p)
            }
            if (vf == this) {
                val defaultConfig = folder.findChild(PROPERTIES_DEFAULT_FILENAME)
                if (defaultConfig != null) {
                    val p = Properties()
                    p.load(InputStreamReader(defaultConfig.inputStream, Charset.forName("UTF-8")))
                    all.add(p)
                }
            }
        }

        if (vf == this)
            break
        vf = vf.parent
    }

    return all
}

private fun VirtualFile.loadCss(path: VirtualFile): String {

    val defaultCss = loadContent(path, CSS_DEFAULT_FILENAME)
    val css = loadContent(path, CSS_FILENAME)

    return if (css.isBlank())
        defaultCss
    else
        defaultCss + "\n" + css
}

private fun VirtualFile.loadPicture(path: VirtualFile)
    = loadContent(path, EXPORT_PICTURE_FILENAME, EXPORT_PICTURE_DEFAULT_FILENAME)

private fun VirtualFile.loadTemplate(path: VirtualFile)
    = loadContent(path, EXPORT_TEMPLATE_FILENAME, EXPORT_TEMPLATE_DEFAULT_FILENAME)

private fun VirtualFile.loadFont(path: VirtualFile)
    = load(path, FONT, FONT_DEFAULT)

private fun VirtualFile.loadContent(path: VirtualFile, fileName: String, defaultFileName: String? = null)
    = load(path, fileName, defaultFileName)?.contentsToByteArray()?.toString(Charsets.UTF_8) ?: ""

private fun VirtualFile.load(path: VirtualFile, fileName: String, defaultFileName: String? = null): VirtualFile? {

    var vf: VirtualFile? = path
    while (vf != null) {

        val folder = vf.findChild(CONFIG_FOLDER)
        if (folder != null) {
            val css = folder.findChild(fileName)
            if (css != null) {
                return css
            }
            if (vf == this && defaultFileName!=null) {
                val defaultCss = folder.findChild(defaultFileName)
                if (defaultCss != null) {
                    return defaultCss
                }
            }
        }

        if (vf == this)
            break
        vf = vf.parent
    }

    return null
}


fun createConfiguration(
    propertiesFiles: List<Properties>,
    css: String,
    picture: String,
    template : String,
    font: VirtualFile
): Config {

    fun get(property: String): String {
        propertiesFiles.forEach {
            val v = it.getProperty(property, null)
            if (v != null)
                return v
        }
        return ""
    }

    fun getBoolean(property: String): Boolean {
        propertiesFiles.forEach {
            val v = it.getProperty(property, null)
            if (v != null)
                return "True".toUpperCase() == v.trim()
        }
        return false
    }

    val frontpage =
        propertiesFiles
            .flatMap { it.stringPropertyNames() }
            .filter { it.startsWith("export.frontpage.") }
            .map { it.substring(17) to get(it) }
            .toMap()

    return Config(
        font = font,
        topLeft = get("export.topLeft"),
        topCenter = get("export.topCenter"),
        topRight = get("export.topRight"),
        topFontSize = get("export.topFontSize"),
        bottomLeft = get("export.bottomLeft"),
        bottomCenter = get("export.bottomCenter"),
        bottomRight = get("export.bottomRight"),
        bottomFontSize = get("export.bottomFontSize"),
        dateFormat = get("export.dateFormat"),
        summaryTitle = get("export.summary.title"),
        summaryDepth = ESummaryDepth.valueOf(get("export.summary.depth")),
        summaryLeader = ELeader.valueOf(get("export.summary.leader")),
        summaryFontSize = get("export.summary.fontSize"),
        summaryListStyles = get("export.summary.listStyles").split(",").map { it.trim() },
        css = css,
        picture = Picture("Tzatziki", picture, "svg"),
        template = template,
        frontpage = frontpage,
        language = get("export.language"))
}

class Config(
    val font: VirtualFile,
    val topFontSize: String,
    val bottomFontSize: String,

    val topLeft: String,
    val topCenter: String,
    val topRight: String,

    val bottomLeft: String,
    val bottomCenter: String,
    val bottomRight: String,

    val dateFormat: String,

    val css: String,

    val picture: Picture,
    val template: String,
    val summaryTitle: String,
    val summaryDepth: ESummaryDepth,
    val summaryLeader: ELeader,
    val summaryListStyles: List<String>,
    val summaryFontSize: String,

    val frontpage: Map<String, String>,
    val language: String) {

    fun buildStyles(): PdfStyle {
        return PdfStyle(
            font = font,
            bodyFontSize = "25px",
            topFontSize = tune(topFontSize),
            bottomFontSize = tune(bottomFontSize),
            topLeft = tune(topLeft),
            topCenter = tune(topCenter),
            topRight = tune(topRight),
            bottomLeft = tune(bottomLeft),
            bottomCenter = tune(bottomCenter),
            bottomRight = tune(bottomRight),
            contentStyle = css,
            summaryDepth = summaryDepth,
            summaryLeader = summaryLeader,
            summaryListStyles = summaryListStyles,
            summaryFontSize = summaryFontSize,
            first = PdfStyle(
                topLeft = "", topCenter="", topRight = "",
                bottomLeft = "", bottomCenter = "", bottomRight = "",
                topFontSize =  tune(bottomFontSize), bottomFontSize = tune(bottomFontSize), bodyFontSize = "32px",
                summaryDepth = summaryDepth, summaryLeader= summaryLeader, summaryFontSize= summaryFontSize, summaryListStyles= summaryListStyles,
                contentStyle = css,
                font = font
            )
        )
    }

    private fun tune(field: String) =
        field.replace("now()", now().format(DateTimeFormatter.ofPattern(dateFormat)))
}