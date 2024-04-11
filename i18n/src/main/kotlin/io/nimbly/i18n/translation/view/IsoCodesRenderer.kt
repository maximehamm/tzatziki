package io.nimbly.i18n.translation.view

import com.intellij.util.ui.UIUtil
import io.nimbly.i18n.translation.engines.Lang
import io.nimbly.i18n.util.TranslationIcons
import java.awt.Component
import java.awt.Font
import java.awt.image.BufferedImage
import javax.swing.*

class IsoCodesRenderer : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

        if (value is Lang) {

            val originalIcon: Icon
            text = value.name

            if (value == Lang.AUTO) {
                originalIcon = TranslationIcons.getFlag(" ")
                font = font.deriveFont(Font.BOLD)
            } else {
                originalIcon = TranslationIcons.getFlag(value.code)
                font = font.deriveFont(Font.PLAIN)
            }

            val img = BufferedImage(18, originalIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
            val g2d = img.createGraphics()
            originalIcon.paintIcon(this, g2d, 0, 0)
            g2d.dispose()

            icon = ImageIcon(img)
        }

        border = BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding())

        return this
    }
}