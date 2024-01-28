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
import com.intellij.util.IconUtil
import javax.swing.Icon

interface TranslationIcons {

    companion object {

        fun getFlag(country: String, scaleRatio: Double = 0.8): Icon? {
            var icon = FLAGS[country + scaleRatio]
            if (icon != null) return icon

            val path = "io/nimbly/i18n/icons/languages/$country.png"
            try {
                var ico = IconLoader.findIcon(path, TranslationIcons::class.java)
                if (ico != null)
                    ico = IconUtil.scale(ico, scaleRatio)
                icon = ico
            } catch (ignored: Throwable) {
            }
            FLAGS[country + scaleRatio] = icon

            return icon
        }

        val FLAGS: MutableMap<String, Icon?> = HashMap()
    }
}
