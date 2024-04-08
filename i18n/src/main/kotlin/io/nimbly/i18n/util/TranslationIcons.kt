/*
 * I18N +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package io.nimbly.i18n.util

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import java.awt.Color
import javax.swing.Icon

interface TranslationIcons {

    companion object {

        fun getFlag(locale: String, scaleRatio: Double = 0.8): ZIcon {

            var icon = FLAGS[locale + scaleRatio]
            if (icon != null) return icon

            try {
                var ico = IconLoader.findIcon("io/nimbly/i18n/icons/languages/svg/$locale.svg", TranslationIcons::class.java)
                if ((ico?.iconWidth ?: 0) < 16) {
                    ico = IconLoader.findIcon("io/nimbly/i18n/icons/languages/svg/${locale.substringBefore("-")}.svg", TranslationIcons::class.java)
                    if ((ico?.iconWidth ?: 0) < 16) {
                        ico = IconLoader.findIcon("io/nimbly/i18n/icons/languages/$locale.png", TranslationIcons::class.java)
                        if ((ico?.iconWidth ?: 0) < 16) {
                            ico = IconLoader.findIcon("io/nimbly/i18n/icons/languages/${locale.substringBefore("-")}.png", TranslationIcons::class.java)
                            if ((ico?.iconWidth ?: 0) < 16) {
                                throw NullPointerException()
                            }
                        }
                    }
                }
                if (ico == null)
                    throw NullPointerException()
                ico = IconUtil.scale(ico, scaleRatio)
                icon = ZIcon(locale, ico, true)
            } catch (ignored: Throwable) {
                val ticon = textToIcon(locale.uppercase(), (scaleRatio * 11f).toFloat(), -1, JBColor.GRAY)
                icon = ZIcon(locale, ticon, false)
            }
            FLAGS[locale + scaleRatio] = icon!!

            return icon
        }

        private val FLAGS: MutableMap<String, ZIcon?> = HashMap()
    }
}

class ZIcon(
    val locale: String,
    icon: Icon,
    val flag: Boolean = true
) : Icon by icon
