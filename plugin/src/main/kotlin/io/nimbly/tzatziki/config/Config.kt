package io.nimbly.tzatziki.config

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.updateSettings.impl.UpdateChecker.getNotificationGroup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import io.nimbly.tzatziki.pdf.PdfStyle
import io.nimbly.tzatziki.util.now
import io.nimbly.tzatziki.util.warn
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.event.HyperlinkEvent

const val CONFIG_FILE_NAME = "cucumber+.properties"

fun loadConfig(file: GherkinFile): ConfigDTO{

    val project = file.project
    val root = ProjectFileIndex.SERVICE.getInstance(project).getSourceRootForFile(file.virtualFile)
        ?: return DEFAULT_CONFIG

    var noConfigYet = true
    var vf = file.virtualFile
    while (vf != null) {

        val found = vf.findChild(CONFIG_FILE_NAME)
        if (found != null) {
            noConfigYet = false
            val load = load(found)
            if (load != null)
                return load
        }

        if (vf == root)
            break

        vf = vf.parent
    }

    if (noConfigYet) {

        WriteCommandAction.runWriteCommandAction(project) {

            val config = root.createChildData(file, CONFIG_FILE_NAME)
            DEFAULT_CONFIG.saveAsProperties(config)

            getNotificationGroup().createNotification(
                "Cucumber+",
                "<html>Configuration file was <a href='${config.path}'>generated</a></html>",
                NotificationType.INFORMATION, { _: Notification?, event: HyperlinkEvent ->
                    val path = event.description
                    PsiManager.getInstance(project).findFile(config)!!.navigate(true)
                },
                "io.nimbly.notification"
            ).notify(project)
        }

        return DEFAULT_CONFIG
    }
    else {
        warn("Using default configuration is corrupted.\n" +
            "Check your '$CONFIG_FILE_NAME' file", project
        )
        return DEFAULT_CONFIG
    }
}

private fun load(config: VirtualFile): ConfigDTO? {
    try {
        val p = Properties()
        p.load(config.inputStream)
        return ConfigDTO(p)
    }
    catch (e: Exception) {
        return null
    }
}

object DEFAULT_CONFIG : ConfigDTO()

open class ConfigDTO(
    val topFontSize: String = "16px",
    val bottomFontSize: String = "12px",

    val topLeft: String = "Nimbly",
    val topCenter: String = "",
    val topRight: String = "now()",
//    val topRight: String = "'now().format(DateTimeFormatter.ofPattern(\"dd-MM-yyyy\"))}",

    val bottomLeft: String = "Cucumber+",
    val bottomCenter: String = "",
    val bottomRight: String = "'Page ' counter(page) ' / ' counter(pages);",

    val dateFormat: String = "dd-MM-yyyy") {

    constructor(config: Properties) :
        this(
            topFontSize = config.getProperty("topFontSize"),
            bottomFontSize = config.getProperty("bottomFontSize"),
            topLeft = config.getProperty("topLeft"),
            topCenter = config.getProperty("topCenter"),
            topRight = config.getProperty("topRight"),
            bottomLeft = config.getProperty("bottomLeft"),
            bottomCenter = config.getProperty("bottomCenter"),
            bottomRight = config.getProperty("bottomRight"),
            dateFormat = config.getProperty("dateFormat")
        )

    fun saveAsProperties(file: VirtualFile) {
        val props = Properties()
        props.setProperty("topFontSize", topFontSize)
        props.setProperty("bottomFontSize", bottomFontSize)
        props.setProperty("topLeft", topLeft)
        props.setProperty("topCenter", topCenter)
        props.setProperty("topRight", topRight)
        props.setProperty("bottomLeft", bottomLeft)
        props.setProperty("bottomCenter", bottomCenter)
        props.setProperty("bottomRight", bottomRight)
        props.setProperty("dateFormat", dateFormat)

        val outputStream = file.getOutputStream(this)
        props.store(outputStream,"""
             -----------
              Cucumber+ 
             -----------
            
               If you need specific configuration for aspecific Cucumber feature, 
               you can move this files closer from feature : 
               Cucumber+ will look for its configuration file into your feature folder,
               and if not found, it will go recursivly to parent's folder until it will 
               find the expected configuration file. If classpath root folder is reached,
               then Cucumber+ will stop searching and will create a default file.
            
             -----------
            """.trimIndent())
        outputStream.close()
    }

    fun buildStyles(): PdfStyle {

        return PdfStyle(
            bodyFontSize = "25px",
            topFontSize = tune(topFontSize),
            bottomFontSize = tune(bottomFontSize),
            topLeft = tune(topLeft),
            topCenter = tune(topCenter),
            topRight = tune(topRight),
            bottomLeft = tune(bottomLeft),
            bottomCenter = tune(bottomCenter),
            bottomRight = tune(bottomRight),
            dateFormat = tune(dateFormat),
            contentStyle = //language=CSS
            """
                * { font-size: 16px; margin: 0 0 0 0 }
                p { margin: 0 }

                table { margin-top: 10px; margin-left: 10px; margin-right: 10px; max-width: 100%; }
                table, th, td {  
                    font-size: 14px; 
                    vertical-align: top; 
                    border: 1px solid midnightblue;  
                    border-collapse: collapse;  
                }  
                th, td { padding: 5px; white-space: break-spaces; }
                th { color: chocolate }
                
                div { display: inline-block; }
                
                .feature { margin-left: 5px; }
                .featureTitle { font-size: 24px; margin-bottom: 10px; }
                .featureHeader { margin-left: 5px; font-weight: bolder }
                
                .rule { margin-left: 10px; }
                .ruleTitle { font-size: 24px; margin-bottom: 10px; }
                
                .scenario { margin-left: 15px; }
                .scenarioTitle { font-size: 20px; border-bottom: 10px;  }
                
                .step { margin-left: 20px; }
                .stepParameter { color: chocolate; font-weight: bolder }
                
                .examples { margin-left: 20px; }
                .stepKeyword { color: grey; }
                
                .docstringMargin { margin-left: 15px; border-left: thick solid chocolate; }
                .docstring { margin-left: 10px; font-family: monospace; font-size: 12px; 
                    letter-spacing: -1px; }
                
                .comment { color: grey}
                """.trimIndent()
        )
    }

    private fun tune(field: String) =
        field.replace("now()", now().format(DateTimeFormatter.ofPattern(dateFormat)))
}

