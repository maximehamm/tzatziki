/*
 * @author Maxime HAMM
 * Copyright (c) 2013. All rights reserved.
 * This file is part of Jspresso Developer Studio
 * 
 * Jspresso Developer Studio is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by the 
 * Free Software Foundation, either version 3 of the License, or (at your 
 * option) any later version. 
 * Jspresso Developer Studio is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE.  
 * 
 * Seethe GNU General Public License for more details. You should have 
 * received a copy of the GNU General Public License along with Jspresso Developer Studio.  
 * If not, http://www.gnu.org/licenses/.
 */
package io.nimbly.tzatziki.util

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingConstants

object CommonIcons {
    val TRANSPARENT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/transparent.png", javaClass)
    val COUNT_BASE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/usagesBase.png", javaClass)
}

/**
 * Add text
 */
fun addText(base: Icon, text: String, size: Float, position: Int, foreground: Color): Icon {
    val icon = LayeredIcon(2)
    icon.setIcon(base, 0)
    icon.setIcon(textToIcon(text, JLabel(), JBUIScale.scale(size), foreground), 1, position)
    return icon
}

/**
 * textToIcon
 */
fun textToIcon(text: String, component: Component, fontSize: Float, foreground: Color): Icon {
    val font: Font = JBFont.create(JBUI.Fonts.label().deriveFont(fontSize))
    val metrics = component.getFontMetrics(font)
    val width = metrics.stringWidth(text) + JBUI.scale(4)
    val height = metrics.height
    return object : Icon {
        override fun paintIcon(c: Component?, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create()
            try {
                GraphicsUtil.setupAntialiasing(g)
                g.font = font
                UIUtil.drawStringWithHighlighting(
                    g,
                    text,
                    x + JBUI.scale(2),
                    y + height - JBUI.scale(1),
                    foreground,
                    JBColor.background()
                )
            } finally {
                g.dispose()
            }
        }

        override fun getIconWidth(): Int {
            return width
        }

        override fun getIconHeight(): Int {
            return height
        }
    }
}

fun getScaleFactorToFit(original: Dimension?, toFit: Dimension?): Double {
    var dScale = 1.0
    if (original != null && toFit != null) {
        val dScaleWidth = getScaleFactor(original.width, toFit.width)
        val dScaleHeight = getScaleFactor(original.height, toFit.height)
        dScale = Math.min(dScaleHeight, dScaleWidth)
    }
    return dScale
}

private fun getScaleFactor(iMasterSize: Int, size: Int)
    = size.toDouble() / iMasterSize.toDouble()

/**
 * scale
 */
fun scale(source: Icon, factor: Float)
    = IconUtil.scale(source, null, factor)


private var numberIcons: MutableMap<String, Icon> = HashMap()
fun getNumberIcon(index: Int, foreground: Color): Icon {
    val key = "$index/$foreground"
    var icon = numberIcons[key]
    if (icon == null) {
        icon = addText(
            CommonIcons.COUNT_BASE, //TRANSPARENT,
            "" + index,
            10f,
            SwingConstants.CENTER,
            foreground
        )
        numberIcons[key] = icon
    }
    return icon
}
