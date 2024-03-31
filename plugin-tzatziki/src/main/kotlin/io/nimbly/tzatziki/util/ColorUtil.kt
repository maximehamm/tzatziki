package io.nimbly.tzatziki.util

import java.awt.Color
import kotlin.math.max
import kotlin.math.min

@Suppress("UseJBColor")
fun Color.darker(factor: Double = 0.8): Color {
    return Color(
        max((red * factor).toInt(), 0),
        max((green * factor).toInt(), 0),
        max((blue * factor).toInt(), 0),
        alpha
    )
}

@Suppress("UseJBColor")
fun Color.brighter(factor: Double = 0.8): Color {
    var r = red
    var g = green
    var b = blue
    val alpha = alpha

    /* From 2D group:
    * 1. black.brighter() should return grey
    * 2. applying brighter to blue will always return blue, brighter
    * 3. non pure color (non zero rgb) will eventually return white
    */
    val i = (1.0 / (1.0 - factor)).toInt()
    if (r == 0 && g == 0 && b == 0) {
        return Color(i, i, i, alpha)
    }
    if (r in 1 until i) r = i
    if (g in 1 until i) g = i
    if (b in 1 until i) b = i

    return Color(
        min((r / factor).toInt(), 255),
        min((g / factor).toInt(), 255),
        min((b / factor).toInt(), 255),
        alpha
    )
}
