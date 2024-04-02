package io.nimbly.i18n.translation.view

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.UIUtil
import io.nimbly.i18n.translation.engines.Lang
import io.nimbly.i18n.util.TranslationIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.plaf.basic.BasicComboBoxEditor

class IsoCodesComboBoxEditor : BasicComboBoxEditor() {

    private val label = JBLabel()
    private val panel = JBPanelWithEmptyText(BorderLayout())

    init {
        panel.add(label, BorderLayout.CENTER)
        panel.isOpaque = true
        panel.background = UIUtil.getListBackground()
    }

    override fun getEditorComponent(): Component {
        return panel
    }

    override fun getItem(): Any? {
        return label.text
    }

    override fun setItem(anObject: Any?) {
        if (anObject is Lang) {
            label.text = anObject.name
            val originalIcon = TranslationIcons.getFlag(anObject.code)!!
            val image = BufferedImage(18, originalIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
            val g2d = image.createGraphics()
            originalIcon.paintIcon(this.panel, g2d, 0, 0)
            g2d.dispose()
            label.icon = ImageIcon(image)
        }
    }

    override fun selectAll() {
        // Do nothing or implement text selection if needed
    }

    override fun addActionListener(l: ActionListener) {
        // This might not be needed depending on your requirements
    }

    override fun removeActionListener(l: ActionListener) {
        // This might not be needed depending on your requirements
    }
}