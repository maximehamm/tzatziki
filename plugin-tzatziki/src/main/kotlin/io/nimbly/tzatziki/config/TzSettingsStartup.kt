/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL

/**
 * Applies the persisted master switch ([TzSettings.State.enabled]) to the runtime
 * [TOGGLE_CUCUMBER_PL] flag at startup, so a "disabled" setting survives restarts (the flag itself is
 * a plain in-memory var defaulting to `true`). The Settings page and the toggle action keep the two
 * in sync afterwards.
 */
class TzSettingsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        TOGGLE_CUCUMBER_PL = TzSettings.getInstance().state.enabled
    }
}
