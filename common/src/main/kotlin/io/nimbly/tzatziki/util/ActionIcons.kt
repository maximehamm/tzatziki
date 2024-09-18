/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

import com.intellij.ui.IconManager

// See Intellij Icons here : https://jetbrains.design/intellij/resources/icons_list/
object ActionIcons {

    val CUCUMBER = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber.svg", javaClass)
    val CUCUMBER_PLUS_16 = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber-plus-16x16.png", javaClass)
}
