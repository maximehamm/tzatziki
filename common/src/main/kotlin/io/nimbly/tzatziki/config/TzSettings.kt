/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persisted, application-level Cucumber+ settings (Settings → Tools → Cucumber+). Each "active" editor
 * behaviour can be turned off; the [enabled] master switch turns them all off at once.
 *
 * Every feature gate goes through the `is…Enabled()` helpers so a single place enforces
 * "master AND per-feature".
 */
@State(name = "CucumberPlusSettings", storages = [Storage("cucumber-plus.xml")])
class TzSettings : PersistentStateComponent<TzSettings.State> {

    class State {
        /** Master switch — when off, ALL the optional behaviours below are disabled. */
        var enabled: Boolean = true
        /** Proactive "Rename steps and references…" inlay shown while editing a bound step. */
        var renameSuggestion: Boolean = true
        /** Auto-format Gherkin tables while typing (+ smart copy/paste alignment). */
        var tableAutoFormat: Boolean = true
        /** Smart table keys: Pipe → new column, Enter → new row, Tab → cell navigation, Del. */
        var smartTableKeys: Boolean = true
        /** Smart table copy / cut / paste (column-aware clipboard, Excel-friendly). */
        var smartClipboard: Boolean = true
        /** Auto-switch the editor to column (rectangular) selection mode inside a Gherkin table. */
        var autoColumnMode: Boolean = true
        /** Drag-and-drop reordering of table rows (left frame) and columns (top frame). */
        var dragAndDrop: Boolean = true
        /** Draw the frame (border decoration) around Gherkin tables. */
        var tableFrame: Boolean = true
        /** Gherkin ↔ step-definition breakpoint synchronisation. */
        var breakpointSync: Boolean = true
        /** "Used by N scenarios" gutter markers on step definitions. */
        var usageMarkers: Boolean = true
    }

    private var state = State()
    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    fun isRenameSuggestionEnabled(): Boolean = state.enabled && state.renameSuggestion
    fun isTableAutoFormatEnabled(): Boolean = state.enabled && state.tableAutoFormat
    fun isSmartTableKeysEnabled(): Boolean = state.enabled && state.smartTableKeys
    fun isSmartClipboardEnabled(): Boolean = state.enabled && state.smartClipboard
    fun isAutoColumnModeEnabled(): Boolean = state.enabled && state.autoColumnMode
    fun isDragAndDropEnabled(): Boolean = state.enabled && state.dragAndDrop
    fun isTableFrameEnabled(): Boolean = state.enabled && state.tableFrame
    fun isBreakpointSyncEnabled(): Boolean = state.enabled && state.breakpointSync
    fun isUsageMarkersEnabled(): Boolean = state.enabled && state.usageMarkers

    companion object {
        /** Fallback (all features on) when the application service isn't registered — e.g. light test
         *  fixtures that don't load the full plugin descriptor. */
        private val DEFAULT = TzSettings()

        @JvmStatic
        fun getInstance(): TzSettings =
            ApplicationManager.getApplication()?.getService(TzSettings::class.java) ?: DEFAULT
    }
}
