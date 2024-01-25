/*
 * I18N
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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
package io.nimbly.tzatziki.util

import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import javax.swing.Icon

interface I18NIcons {

    companion object {

        fun getFlag(country: String): Icon? {
            var icon = FLAGS[country]
            if (icon != null) return icon

            val path = "io/nimbly/tzatziki/icons/languages/$country.png"
            try {
                var ico = IconLoader.findIcon(path, javaClass)
                if (ico != null)
                    ico = IconUtil.scale(ico, 0.8)
                icon = ico
            } catch (ignored: Throwable) {
            }
            FLAGS[country] = icon

            return icon
        }

        val FLAGS: MutableMap<String, Icon?> = HashMap()
    }
}
