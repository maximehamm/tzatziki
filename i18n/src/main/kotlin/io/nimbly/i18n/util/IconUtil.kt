package io.nimbly.i18n.util

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.UIManager

fun textToIcon(text: String, size: Float, position: Int, foreground: Color): Icon {
    val icon = LayeredIcon(2)
    icon.setIcon(textToIcon(text, JLabel(), JBUIScale.scale(size), foreground, position), 1, 0)
    return icon
}

fun textToIcon(text: String, component: Component, fontSize: Float, foreground: Color, position: Int): Icon {
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
                    y + height - JBUI.scale(1) + position,
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

fun Icon.toBase64(): String {
    val bufferedImage = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)

    // Create a graphics context and paint the icon on the buffered image
    val graphics = bufferedImage.createGraphics()
    paintIcon(null, graphics, 0, 0)
    graphics.dispose()

    // Write the buffered image to a byte array
    val byteArrayOutputStream = ByteArrayOutputStream()

    // Determine the image format based on the icon data type
    val imageFormat = if (this is ImageIcon) "jpg" else "png"

    // Write the image data to the byte array
    ImageIO.write(bufferedImage, imageFormat, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()

    // Encode the byte array to a base64 string
    val base64String = Base64.getEncoder().encodeToString(byteArray)

    return base64String
}

fun Icon.addVeilToIcon(): Icon {

        val veilColor = if (UIUtil.isUnderDarcula()) Color(0, 0, 0, 128) else Color(255, 255, 255, 128)

        val width = iconWidth
        val height = iconHeight

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()
        paintIcon(null, graphics, 0, 0)

        // Draw a semi-transparent veil rectangle over the icon
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f)
        graphics.color = veilColor
        graphics.fillRect(0, 0, width, height)

        // Cleanup graphics object
        graphics.dispose()

        return object : Icon {
            override fun paintIcon(c: java.awt.Component?, g: Graphics?, x: Int, y: Int) {
                g?.drawImage(bufferedImage, x, y, null)
            }

            override fun getIconWidth(): Int {
                return width
            }

            override fun getIconHeight(): Int {
                return height
            }
        }
    }