package io.nimbly.i18n.preferences

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.first
import com.jetbrains.rd.util.firstOrNull
import io.nimbly.i18n.TranslationPlusSettings
import io.nimbly.i18n.translation.TranslationManager
import io.nimbly.i18n.translation.engines.IEngine
import io.nimbly.i18n.translation.engines.Lang
import io.nimbly.i18n.translation.engines.TranslationEngineFactory
import io.nimbly.i18n.util.TranslationIcons
import io.nimbly.i18n.util.ZIcon
import io.nimbly.i18n.util.nullIfEmpty
import java.awt.*
import java.awt.FlowLayout.LEFT
import java.awt.event.*
import javax.swing.*
import javax.swing.AbstractAction.NAME
import javax.swing.ScrollPaneConstants.*
import javax.swing.event.HyperlinkEvent
import javax.swing.plaf.LabelUI

class TranslationPlusOptionsConfigurable : SearchableConfigurable, Configurable.NoScroll {

    private val LOG = logger<TranslationPlusOptionsConfigurable>()

    private var main: JPanel? = null
    private val checkBoxes: MutableList<Pair<IEngine, JBCheckBox>> = mutableListOf()
    private val keys: MutableList<Pair<IEngine, JBPasswordField>> = mutableListOf()

    private val mySettings = TranslationPlusSettings.getSettings()
    private var testAction: AbstractAction? = null

    private val languageFilterListeners = mutableListOf<IFilterListener>()

    override fun createComponent(): JComponent? {

         if (main != null)
             return main

        languageFilterListeners.clear()

        main = JPanel(GridLayoutManager(3, 1))

        main!!.add(buildTitlePanel(), GridConstraints(
            0, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK,
                SIZEPOLICY_CAN_SHRINK,
            null, null, null
        ))

        main!!.add(buildEnginePanel(), GridConstraints(
                1, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
            SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                null, null, null
            )
        )

        main!!.add(buildTestPanel(), GridConstraints(
                2, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_GROW,
                null, null, null
            ))

        return main
    }

    private fun buildEnginePanel(): JComponent {

        val p = JPanel(GridLayoutManager(
            TranslationEngineFactory.engines().size, 1, JBInsets(0, 0, 0 , 0), 0, 15))

        TranslationEngineFactory.engines().forEachIndexed { index, engine ->
            p.add(buildEnginePanel(engine), GridConstraints(
                index, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                null, null, null))
        }

        return JBScrollPane(
            p,
            VERTICAL_SCROLLBAR_AS_NEEDED,
            HORIZONTAL_SCROLLBAR_NEVER
        )
    }

    private fun buildTitlePanel(): JPanel {
        val p = JPanel(GridLayoutManager(1, 2, JBInsets(0, 0, 0 , 0), 10, 0))
        p.add(JBLabel("""<html>
                Choose your prefered translator engine. Enter the API Key if requested. Filter languages :
                </html>""".trimMargin()
                ), GridConstraints(
            0, 0, 1, 1,
            ANCHOR_CENTER, FILL_NONE,
            SIZEPOLICY_CAN_SHRINK,
            SIZEPOLICY_CAN_SHRINK,
            null, null, null
        ))
        val filter = JBTextField()
        filter.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) {
                updateLanguagesFilter(filter.text.trim().nullIfEmpty())
            }
        })
        p.add(filter, GridConstraints(
            0, 1, 1, 1,
            ANCHOR_CENTER, FILL_NONE,
            SIZEPOLICY_CAN_SHRINK,
            SIZEPOLICY_CAN_SHRINK,
            null, Dimension(100, 26), null
        ))
        return p
    }

    private fun buildTestPanel(): JPanel {

        var text = "Happy new Year !"
        val testLabel = JBLabel(text)
        val foreground = testLabel.foreground
        val p = JPanel(GridLayoutManager(1, 2, JBInsets(0, 5, 10 , 5), 10, 0))

        testAction = object : AbstractAction("Test", TranslationIcons.getFlag("fr")) {
            override fun actionPerformed(e: ActionEvent) {

                apply()
                val engine = checkBoxes.firstOrNull { it.second.isSelected }?.first ?: return
                var lang = (testAction!!.getValue(SMALL_ICON) as ZIcon).locale
                if (engine.languages().get(lang) == null) {
                    lang = engine.languagesToIso639().filter { it.value == "fr" }.firstOrNull()?.key ?: engine.languages().first().key
                }

                testLabel.foreground = foreground

                try {
                    val translation = engine.translate(
                        lang,
                        Lang.AUTO.code,
                        text
                    )
                    if (translation != null) {
                        text = translation.translated
                        testLabel.text = text
                        val newLang = engine.languages().keys.filter { it != lang }.random()
                        testAction!!.putValue(SMALL_ICON, TranslationIcons.getFlag(newLang, engine = engine))
                        testAction!!.putValue(NAME, "Test translation to " + engine.languages()[newLang])
                    }
                } catch (e: Exception) {
                    LOG.info("Build test panel error : " + e.message)
                    testLabel.text = e.message
                    testLabel.foreground = JBColor.RED
                }
            }
        }
        testAction!!.putValue(Action.NAME, "Test translation to French")

        p.add(JButton(testAction), GridConstraints(
                0, 0, 1, 1,
                ANCHOR_NORTHWEST, FILL_HORIZONTAL,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_GROW,
                null, null, null
            ))
        p.add(testLabel, GridConstraints(
                0, 1, 1, 1,
                ANCHOR_WEST, FILL_HORIZONTAL,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_GROW,
                null, null, null
            ))

        return p
    }

    private fun buildEnginePanel(engine: IEngine): JPanel {

        val p = JPanel(GridLayoutManager(3, 2, JBInsets(0, 5, 0, 5), 5, 0))
        p.border = BorderFactory.createMatteBorder(0, 1, 0, 0, CommentLabel("").foreground)

        val check = JBCheckBox(engine.label(), false)
        p.add(
            check, GridConstraints(
                0, 0, 2, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                null, Dimension(170, 30), null
            )
        )

        var key: JBPasswordField? = null
        if (engine.needApiKey()) {
            key = JBPasswordField()
            p.add(
                key, GridConstraints(
                    0, 1, 1, 1,
                    ANCHOR_NORTHWEST, FILL_NONE,
                    SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                    SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                    null, Dimension(300, 30), null
                )
            )
        } else {
            p.add(
                CommentLabel("No API Key required"), GridConstraints(
                    0, 1, 1, 1,
                    ANCHOR_NORTHWEST, FILL_NONE,
                    SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                    SIZEPOLICY_CAN_SHRINK,
                    null, Dimension(300, 30), null, 1
                )
            )
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
        p.add(
            doc, GridConstraints(
                1, 1, 1, 1,
                ANCHOR_NORTHWEST, FILL_NONE,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                SIZEPOLICY_CAN_SHRINK,
                null, null, null, 1
            )
        )

        val langs = object : JBPanelWithEmptyText(), IFilterListener {

            override fun filterChanged(filter: String?) {

                this.components
                    .filterIsInstance<JBLabel>()
                    .forEach {
                        val zIcon = it.icon as ZIcon
                        val visible = filter == null || (zIcon.locale.lowercase().contains(filter.lowercase()))
                        it.isVisible = visible
                    }

                val d = this.calculateDimension()
                this.preferredSize = d
                this.isVisible = d != null
                p.isVisible = d != null
            }

            fun calculateDimension(): Dimension? {
                var empty = true
                var lines = 1
                var l = 0
                this.components
                    .filterIsInstance<JBLabel>()
                    .forEach {
                        if (!it.isVisible)
                            return@forEach

                        empty = false
                        l += it.icon.iconWidth + 6
                        if (l >= 650) {
                            lines++
                            l -= 650
                        }
                    }
                return if (empty) null else Dimension(650, lines * (12 + 6))
            }
        }
        langs.layout = FlowLayout(LEFT, 5, 5)
        langs.border = JBUI.Borders.emptyTop(3)

        engine.languages()
            .map { TranslationIcons.getFlag(it.key, engine = engine) to it.value }
            .sortedBy { (if (it.first.flag) "A" else "Z") + "#" + it.second }
            .forEach { (flag, lang) ->
                val flagLabel = JBLabel(flag)
                flagLabel.apply {
                    toolTipText = "$lang (${flag.locale.uppercase()})"
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) {
                            testAction!!.putValue(AbstractAction.SMALL_ICON, flag)
                            testAction!!.putValue(NAME, "Test translation to " + engine.languages()[flag.locale])

                            val checkBox = checkBoxes.first { it.first == engine }.second
                            checkBox.isSelected = true
                            syncCheckboxes(checkBox)
                        }
                    })
                }
                langs.add(flagLabel)
            }
        addFilterListener(langs)

//        val w = engine.languages()
//            .map { TranslationIcons.getFlag(it.key) }
//            .map { it.iconWidth + 5 }
//            .sum()
//        val dimension = Dimension(650, (w / 650 + 1)  * (13+10))
//        langs.maximumSize = Dimension(650, 10000)
//        langs.preferredSize = dimension

        langs.maximumSize = Dimension(650, 10000)
        langs.preferredSize = langs.calculateDimension()

        p.add(
            langs, GridConstraints(
                2, 0, 1, 2,
                ANCHOR_NORTHWEST, FILL_VERTICAL,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW,
                SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW or SIZEPOLICY_WANT_GROW,
                null, null, Dimension(650, 10000), 2
            )
        )

        check.addActionListener { event ->
            key?.isEnabled = check.isSelected
            syncCheckboxes(check)

            val selectedEngine = checkBoxes.first { it.second.isSelected }.first
            val newLang = selectedEngine.languages().keys.random()
            testAction!!.putValue(AbstractAction.SMALL_ICON, TranslationIcons.getFlag(newLang, engine = engine))
        }

        check.isSelected = engine.type == mySettings.activeEngine
        key?.text = mySettings.keys[engine.type]
        key?.isEnabled = check.isSelected

        checkBoxes.add(engine to check)
        if (key != null)
            keys.add(engine to key)

        return p
    }

    private fun syncCheckboxes(check: JBCheckBox) {
        if (check.isSelected)
            checkBoxes.filter { it.second != check }.forEach { it.second.isSelected = false }
        else
            checkBoxes.first().second.isSelected = true
    }

    override fun apply() {
        mySettings.activeEngine = this.checkBoxes.first { it.second.isSelected }.first.type
        mySettings.keys = this.keys.map { it.first.type to it.second.text.trim() }.toMap()

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

    private fun addFilterListener(listener: IFilterListener) {
        this.languageFilterListeners.add(listener)
    }

    private fun updateLanguagesFilter(filter: String?) {
        this.languageFilterListeners.forEach {
            it.filterChanged(filter)
        }
    }
}

interface IFilterListener {
    fun filterChanged(filter: String?)
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
