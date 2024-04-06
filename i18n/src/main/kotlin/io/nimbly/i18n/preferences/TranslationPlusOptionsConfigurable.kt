package io.nimbly.i18n.preferences

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.setEmptyState
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.GridBag
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.TranslationEngineFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

// See example : ConsoleConfigurable, AutoImportOptionsConfigurable
class TranslationPlusOptionsConfigurable : SearchableConfigurable, Configurable.NoScroll {

    private var main: JPanel? = null
    private val checkBoxes: MutableList<Pair<IEngine, JBCheckBox>> = mutableListOf()
    private val keys: MutableList<Pair<IEngine, JBPasswordField>> = mutableListOf()

    private val mySettings = TranslationPlusSettings.getSettings()

    override fun createComponent(): JComponent? {

         if (main != null)
             return main

        val p = JPanel(GridBagLayout())
        val gridBag = GridBag()
        gridBag.anchor(GridBagConstraints.NORTHWEST).setDefaultAnchor(GridBagConstraints.NORTHWEST)

        TranslationEngineFactory.engines().forEach { engine ->

            p.add(buildEnginePanel(engine), gridBag.nextLine().next())
            p.add(Box.createHorizontalGlue(), gridBag.next().coverLine())
        }

        main = JPanel(GridLayoutManager(2, 1))

        main!!.add(
            JBLabel("""<html>
                Choose your prefered translator engine. Enter the API Key if requested.
                </html>""".trimMargin()
        ), GridConstraints(
            0, 0, 1, 1,
            GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_CAN_SHRINK,
            null, null, null
        ))
        main!!.add(p, GridConstraints(
            1, 0, 1, 1,
            GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_WANT_GROW,
          null, null, null
        ))

        return main
    }

    private fun buildEnginePanel(engine: IEngine): JPanel {

        val p = JPanel(GridBagLayout())
        val gridBag = GridBag()
            .anchor(GridBagConstraints.LINE_START)
            .setDefaultAnchor(GridBagConstraints.WEST)
            .setDefaultPaddingX(10)

        val check = JBCheckBox(engine.label(), false).apply {
            this.preferredSize = Dimension(150, 30)
        }
        val key = JBPasswordField().apply {
            this.preferredSize = Dimension(300, 30)
            this.setEmptyState("API Key")
        }

        p.add(check, gridBag.next())
        if (engine.needApiKey())
            p.add(key, gridBag.next())
        p.add(Box.createHorizontalGlue(), gridBag.next().coverLine())

        val wrapper = JPanel(BorderLayout())
        wrapper.add(p, BorderLayout.WEST)

        check.addActionListener { event ->
            key.isEnabled = check.isSelected
            if (check.isSelected)
                checkBoxes.filter { it.second != check }.forEach { it.second.isSelected = false }
        }

        check.isSelected = engine.type == mySettings.activeEngine
        key.text = mySettings.keys[engine.type]
        key.isEnabled = check.isSelected

        checkBoxes.add(engine to check)
        keys.add(engine to key)

        return wrapper
    }

    override fun apply() {
        mySettings.activeEngine = this.checkBoxes.first { it.second.isSelected }.first.type
        mySettings.keys = this.keys.map { it.first.type to it.second.text }.toMap()
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
