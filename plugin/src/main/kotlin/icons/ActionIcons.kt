/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.IconManager
import javax.swing.Icon

object ActionIcons {

    @JvmField val SHIFT_LEFT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-shift-left-16x16.png", javaClass)
    @JvmField val SHIFT_RIGHT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-shift-right-16x16.png", javaClass)
    @JvmField val SHIFT_UP = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-16x16-shift-up.png", javaClass)
    @JvmField val SHIFT_DOWN = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-16x16-shift-down.png", javaClass)

    @JvmField val INSERT_LINE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-insert-16x16.png", javaClass)
    @JvmField val INSERT_COLUMN = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-insert-16x16.png", javaClass)
    @JvmField val DELETE_LINE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-delete-16x16.png", javaClass)
    @JvmField val DELETE_COLUMN = IconLoader.getIcon("/io/nimbly/tzatziki/icons/culumn-delete-16x16.png", javaClass)

    @JvmField val EXPORT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/export-16x16.png", javaClass)

    @JvmField val CUCUMBER_PLUS = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber-plus.png", javaClass)
    @JvmField val CUCUMBER_PLUS_16 = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber-plus-16x16.png", javaClass)

    @JvmField val TAG = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/tag.svg", javaClass)

    val ImagesFileType = IconManager.getInstance().getIcon("/org/intellij/images/icons/ImagesFileType.svg", javaClass)

}
