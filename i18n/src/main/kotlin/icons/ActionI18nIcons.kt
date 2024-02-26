/*
 * CUCUMBER +
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

package icons

import com.intellij.icons.AllIcons.Actions.AddToDictionary
import com.intellij.ui.IconManager

// See Intellij Icons here : https://jetbrains.design/intellij/resources/icons_list/
// New UI icon mapping : https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html#mapping-entries
// New UI icons svg : https://www.jetbrains.com/intellij-repository/releases
object ActionI18nIcons {

    @JvmField val TRANSLATION_PLUS_16 = IconManager.getInstance().getIcon("/io/nimbly/i18n/icons/translation-plus-16x16.png", javaClass)

    @JvmField val I18N = IconManager.getInstance().getIcon("/io/nimbly/i18n/icons/g_trans.png", javaClass)
    @JvmField val SWAP = IconManager.getInstance().getIcon("/io/nimbly/i18n/icons/swap.svg", javaClass)
    @JvmField val DICO = AddToDictionary
        //IconManager.getInstance().getIcon("/io/nimbly/i18n/icons/dictionary2.svg", javaClass)
}
