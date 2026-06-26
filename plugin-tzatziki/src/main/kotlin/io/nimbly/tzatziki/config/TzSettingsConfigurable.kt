/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.config

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL

/**
 * Settings → Tools → Cucumber+. A master switch on top, then a checkbox per optional behaviour
 * (greyed out while the master is off). Bound to [TzSettings].
 */
class TzSettingsConfigurable : BoundConfigurable("Cucumber+") {

    override fun apply() {
        super.apply()
        // The master switch drives the existing runtime kill-switch used across the plugin, then
        // re-run highlighting so gutter markers / inspections reflect the change immediately.
        TOGGLE_CUCUMBER_PL = TzSettings.getInstance().state.enabled
        io.nimbly.tzatziki.editor.TzTableDecorator.refreshAll()   // redraw table frames per the new settings
        ProjectManager.getInstance().openProjects.forEach { DaemonCodeAnalyzer.getInstance(it).restart() }
    }

    override fun createPanel(): DialogPanel {
        val s = TzSettings.getInstance().state
        // On a Remote Dev backend the painted frame + drag gestures aren't relayed to the thin
        // client (see TzTableDecorator.IS_REMOTE_DEV_HOST), so grey out the two checkboxes that
        // wouldn't take effect there and say why.
        val rd = io.nimbly.tzatziki.editor.IS_REMOTE_DEV_HOST
        lateinit var master: Cell<JBCheckBox>
        return panel {
            row {
                master = checkBox("Enable Cucumber+ editor features")
                    .comment("Turn off every Cucumber+ in-editor behaviour below at once.")
                    .bindSelected(s::enabled)
            }
            indent {
                group("Tables") {
                    row {
                        checkBox("Auto-format while typing")
                            .comment("Aligns table columns as you type.")
                            .bindSelected(s::tableAutoFormat)
                    }
                    row {
                        checkBox("Smart keys (Pipe / Enter / Tab / Del)")
                            .comment("Pipe adds a column, Enter adds a row, Tab navigates cells, Del clears/removes. Off → these keys behave normally.")
                            .bindSelected(s::smartTableKeys)
                    }
                    row {
                        checkBox("Smart copy / cut / paste")
                            .comment("Column-aware, Excel-friendly clipboard. Off → copy/cut/paste behave normally.")
                            .bindSelected(s::smartClipboard)
                    }
                    row {
                        checkBox("Auto-switch to column (rectangular) selection")
                            .comment("Enters column-selection mode when the caret is inside a table. Off → selection mode is left untouched.")
                            .bindSelected(s::autoColumnMode)
                    }
                    row {
                        checkBox("Drag-and-drop reordering of rows and columns")
                            .comment(
                                if (rd) "Not available over Remote Development — drag gestures aren't relayed to the thin client."
                                else "Drag the left frame to move a row, the top frame to move a column. Off → the frame still opens the table menu on click."
                            )
                            .bindSelected(s::dragAndDrop)
                            .enabled(!rd)
                    }
                    row {
                        checkBox("Draw the table frame (border decoration)")
                            .comment(
                                if (rd) "Not available over Remote Development — the painted frame isn't relayed to the thin client."
                                else "The frame around tables (also the surface for the menu / drag gestures). Off → tables render without a frame."
                            )
                            .bindSelected(s::tableFrame)
                            .enabled(!rd)
                    }
                }
                group("Steps & definitions") {
                    row {
                        checkBox("Suggest renaming a step and its references while editing it")
                            .comment("Shows the discreet \"Rename steps and references…\" hint under a step you start editing.")
                            .bindSelected(s::renameSuggestion)
                    }
                    row {
                        checkBox("Synchronise breakpoints between Gherkin steps and step definitions")
                            .bindSelected(s::breakpointSync)
                    }
                    row {
                        checkBox("Show \"used by N scenarios\" gutter markers on step definitions")
                            .bindSelected(s::usageMarkers)
                    }
                }
            }.enabledIf(master.selected)
        }
    }
}
