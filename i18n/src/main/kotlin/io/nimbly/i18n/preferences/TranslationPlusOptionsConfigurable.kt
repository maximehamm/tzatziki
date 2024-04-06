package io.nimbly.i18n.preferences

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.TranslationManager
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.TranslationEngineFactory
import java.awt.*
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.plaf.LabelUI

class TranslationPlusOptionsConfigurable : SearchableConfigurable, Configurable.NoScroll {

    private var main: JPanel? = null
    private val checkBoxes: MutableList<Pair<IEngine, JBCheckBox>> = mutableListOf()
    private val keys: MutableList<Pair<IEngine, JBPasswordField>> = mutableListOf()

    private val mySettings = TranslationPlusSettings.getSettings()

    override fun createComponent(): JComponent? {

//         if (main != null)
//             return main

        val p = JPanel(GridBagLayout())
        val gridBag = GridBag()
        gridBag.anchor(GridBagConstraints.NORTHWEST)
            .setDefaultAnchor(GridBagConstraints.NORTHWEST)
            .setDefaultFill(GridBagConstraints.HORIZONTAL)
            .setDefaultPaddingY(0)

        TranslationEngineFactory.engines().forEach { engine ->
            p.add(buildEnginePanel(engine), gridBag.nextLine().next().insetBottom(10))
        }

        main = JPanel(GridLayoutManager(2, 1))

        main!!.add(
            JBLabel("""<html>
                Choose your prefered translator engine. Enter the API Key if requested.
                </html>""".trimMargin()
        ), GridConstraints(
            0, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                SIZEPOLICY_CAN_SHRINK,
            null, null, null
        ))

        main!!.add(p, GridConstraints(
            1, 0, 1, 1,
            ANCHOR_NORTHWEST, FILL_NONE,
            SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            SIZEPOLICY_CAN_GROW,
          null, null, null
        ))

        return main
    }

    private fun buildEnginePanel(engine: IEngine): JPanel {

        val p = JPanel(GridLayoutManager(2, 2, JBInsets(0, 0, 5, 5), 5, 0))

        val check = JBCheckBox(engine.label(), false)
        p.add(check, GridConstraints(
            0, 0, 2, 1,
            ANCHOR_NORTHWEST, FILL_NONE,
            SIZEPOLICY_FIXED, SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            null, Dimension(170, 30), null))

        var key: JBPasswordField? = null
        if (engine.needApiKey()) {
            key = JBPasswordField()
            p.add(key, GridConstraints(
                    0, 1, 1, 1,
                    ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                    SIZEPOLICY_CAN_SHRINK,
                    null, Dimension(300, 30), null
                ))
        } else {
            p.add(CommentLabel("No API Key required"), GridConstraints(
                0, 1, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                SIZEPOLICY_CAN_SHRINK,
                null, Dimension(300, 30), null, 1
            ))
        }

        val doc = JEditorPane()
        doc.editorKit = JBHtmlEditorKit() // HTMLEditorKitBuilder.simple()
        doc.isEditable = false // Set the JEditorPane to be non-editable
        doc.contentType = "text/html" // Set the content type
        doc.cursor = Cursor(Cursor.HAND_CURSOR)
        doc.text = engine.documentation()
        doc.font = ComponentPanelBuilder.getCommentFont(doc.font)
        doc.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        doc.setOpaque(false)
        doc.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.url)
            }
        }

        p.add(doc, GridConstraints(
            1, 1, 1, 1,
            ANCHOR_NORTHWEST, FILL_HORIZONTAL,
            SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
            SIZEPOLICY_CAN_SHRINK,
            null, null, null, 1
        ))

        check.addActionListener { event ->
            key?.isEnabled = check.isSelected
            if (check.isSelected)
                checkBoxes.filter { it.second != check }.forEach { it.second.isSelected = false }
        }

        check.isSelected = engine.type == mySettings.activeEngine
        key?.text = mySettings.keys[engine.type]
        key?.isEnabled = check.isSelected

        checkBoxes.add(engine to check)
        if (key != null)
            keys.add(engine to key)

        return p
    }

    override fun apply() {
        mySettings.activeEngine = this.checkBoxes.first { it.second.isSelected }.first.type
        mySettings.keys = this.keys.map { it.first.type to it.second.text }.toMap()

        TranslationManager.changeEngine(mySettings.activeEngine)
    }

    override fun getId(): String {
        return displayName
    }

    override fun getDisplayName(): String {
        return "Translation+"
    }

    override fun isModified(): Boolean {
        return true
    }
}

class CommentLabel(text: String) : JBLabel(text) {
    init {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }

    override fun setUI(ui: LabelUI) {
        super.setUI(ui)
        font = ComponentPanelBuilder.getCommentFont(font)
    }
}
