package io.nimbly.i18n.translation

import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.Lang
import io.nimbly.i18n.util.EFormat
import io.nimbly.i18n.util.EStyle
import io.nimbly.i18n.util.TranslationIcons
import io.nimbly.i18n.util.languagesMap

class TranslateErrorToInputAction : TranslateErrorAction() {
    override fun getLanguage(): String {
        return TranslationPlusSettings.getSettings().input
    }
}

class TranslateErrorToOutputAction : TranslateErrorAction() {
    override fun getLanguage(): String {
        return TranslationPlusSettings.getSettings().output
    }
}

abstract class TranslateErrorAction : DumbAwareAction()  {

    abstract fun getLanguage() : String

    override fun update(event: AnActionEvent) {

        val lang = getLanguage()
        val node = event.getData(PlatformCoreDataKeys.SELECTED_ITEM) as? ProblemNode

        event.presentation.isVisible = lang != Lang.AUTO.code
        event.presentation.isEnabled = node != null

        event.presentation.icon = TranslationIcons.getFlag(lang)
        event.presentation.text = "Add ${languagesMap[lang] + " tooltip"}"
    }

    override fun actionPerformed(event: AnActionEvent) {

        val node = event.getData(PlatformCoreDataKeys.SELECTED_ITEM) as? ProblemNode
            ?: return

        val project = CommonDataKeys.PROJECT.getData(event.dataContext)
            ?: return

        val output = getLanguage()
        val translation = TranslationManager.translate(output, Lang.AUTO.code, node.text, EFormat.TEXT, EStyle.NORMAL, null, project)

        val url = javaClass.getResource("/io/nimbly/i18n/icons/languages/${output}.png")

        node.presentation.tooltip =
        """
        <!DOCTYPE html>
        <html>
        <head>
        </head>
        <body>
        <img src="$url" alt="PNG Image" width="15" height="10">
        &nbsp;${translation?.translated}
        </body>
        </html>
        """.trimIndent()
    }
}