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

package icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.IconManager
import javax.swing.Icon

// See Intellij Icons here : https://jetbrains.design/intellij/resources/icons_list/
// New UI icon mapping : https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html#mapping-entries
// New UI icons svg : https://www.jetbrains.com/intellij-repository/releases
object ActionIcons {

    @JvmField val SHIFT_LEFT  = IconLoader.getIcon("/io/nimbly/tzatziki/icons/shift-left.svg", javaClass)
    @JvmField val SHIFT_RIGHT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/shift-right.svg", javaClass)
    @JvmField val SHIFT_UP    = IconLoader.getIcon("/io/nimbly/tzatziki/icons/shift-up.svg", javaClass)
    @JvmField val SHIFT_DOWN  = IconLoader.getIcon("/io/nimbly/tzatziki/icons/shift-down.svg", javaClass)

    @JvmField val ROW_ADD       = IconLoader.getIcon("/io/nimbly/tzatziki/icons/row-add.svg", javaClass)
    @JvmField val ROW_DELETE    = IconLoader.getIcon("/io/nimbly/tzatziki/icons/row-delete.svg", javaClass)
    @JvmField val COLUMN_ADD    = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-add.svg", javaClass)
    @JvmField val COLUMN_DELETE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-delete.svg", javaClass)

    /** Gherkin-side breakpoint icons (full green disc / hollow ring for disabled). */
    @JvmField val BREAKPOINT_CUCUMBER = IconLoader.getIcon("/io/nimbly/tzatziki/icons/breakpoint-cucumber.svg", javaClass)
    @JvmField val BREAKPOINT_CUCUMBER_DISABLED = IconLoader.getIcon("/io/nimbly/tzatziki/icons/breakpoint-cucumber-disabled.svg", javaClass)

    /** Gherkin-side Scenario Outline example breakpoint — green diamond, distinguishable
     *  from the step breakpoint (green disc) without leaving the Cucumber+ palette. */
    @JvmField val BREAKPOINT_CUCUMBER_EXAMPLE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/breakpoint-cucumber-example.svg", javaClass)
    @JvmField val BREAKPOINT_CUCUMBER_EXAMPLE_DISABLED = IconLoader.getIcon("/io/nimbly/tzatziki/icons/breakpoint-cucumber-example-disabled.svg", javaClass)

    /** Tiny green badge layered on top of the standard JetBrains breakpoint icons
     *  to identify Cucumber+'s code-side breakpoints (Java/Kotlin). */
    @JvmField val BREAKPOINT_CUCUMBER_BADGE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/breakpoint-cucumber-badge.svg", javaClass)

    /** Half red / half white breakpoint disc — base for the "partial mute" state (a shared
     *  step definition whose linked Gherkin steps are partly muted). */
    @JvmField val BREAKPOINT_HALF = IconLoader.getIcon("/io/nimbly/tzatziki/icons/breakpoint-half.svg", javaClass)

    /** Java/Kotlin code-side: standard breakpoint icon + Cucumber+ green badge overlay (all states). */
    @JvmField val BREAKPOINT_CUCUMBER_CODE_ENABLED: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_set_breakpoint, BREAKPOINT_CUCUMBER_BADGE)

    /** Code-side "partial mute": half red / half white disc + the filled green Cucumber+
     *  disc, shown via a per-instance customized breakpoint presentation. */
    @JvmField val BREAKPOINT_CUCUMBER_CODE_PARTIAL: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(BREAKPOINT_HALF, BREAKPOINT_CUCUMBER_BADGE)
    @JvmField val BREAKPOINT_CUCUMBER_CODE_DISABLED: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_disabled_breakpoint, BREAKPOINT_CUCUMBER_BADGE)
    @JvmField val BREAKPOINT_CUCUMBER_CODE_MUTED_ENABLED: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_muted_breakpoint, BREAKPOINT_CUCUMBER_BADGE)
    @JvmField val BREAKPOINT_CUCUMBER_CODE_MUTED_DISABLED: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_muted_disabled_breakpoint, BREAKPOINT_CUCUMBER_BADGE)
    @JvmField val BREAKPOINT_CUCUMBER_CODE_NO_SUSPEND: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_no_suspend_breakpoint, BREAKPOINT_CUCUMBER_BADGE)

    /** Java/Kotlin code-side, "verified" state (debugger has installed the JDI request).
     *  Layers the Cucumber+ green badge on top of the standard verified icon so the user
     *  keeps the Cucumber+ identity even when the breakpoint is armed. */
    @JvmField val BREAKPOINT_CUCUMBER_CODE_VERIFIED: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_verified_breakpoint, BREAKPOINT_CUCUMBER_BADGE)
    @JvmField val BREAKPOINT_CUCUMBER_CODE_VERIFIED_NO_SUSPEND: javax.swing.Icon =
        com.intellij.ui.LayeredIcon(com.intellij.icons.AllIcons.Debugger.Db_verified_no_suspend_breakpoint, BREAKPOINT_CUCUMBER_BADGE)

    @JvmField val CUCUMBER_PLUS_64 = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber-plus.png", javaClass)
    @JvmField val CUCUMBER_PLUS_16 = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber-plus-16x16.png", javaClass)
    @JvmField val CUCUMBER_PLUS = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/cucumber-plus.png", javaClass)

    @JvmField val RUN = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/run.svg", javaClass)

    @JvmField val STEP = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/step.svg", javaClass)

    @JvmField val FILTER = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/filter.svg", javaClass)

    @JvmField val TAG = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/tag.svg", javaClass)
    @JvmField val TAG_GRAY = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/tagGray.svg", javaClass)

    @JvmField val PDF = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/pdf.svg", javaClass)

    @JvmField val GROUP_BY_MODULE = IconManager.getInstance().getIcon("/io/nimbly/tzatziki/icons/groupByModule.svg", javaClass)

    val ImagesFileType = IconManager.getInstance().getIcon("/org/intellij/images/icons/ImagesFileType.svg", javaClass)
}
