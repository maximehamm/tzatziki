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
package io.nimbly.tzatziki.services

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.nimbly.tzatziki.util.notification

/**
 * Notifies the user (once per project) about the `.cucumber-scope` mechanism when
 * a Gherkin step still resolves to multiple step definitions despite the AUTO
 * step-scope filter being active.
 *
 * This addresses both UX requests of issue #104:
 *  - "show a tip in the popup when several matches remain" (déclencheur A)
 *  - "show a one-time balloon" (déclencheur B)
 * — converged into a single balloon, since IntelliJ's standard goto-declaration popup
 * is not extensible.
 */
object TzScopeAdvisor {

    /**
     * Call when a Gherkin step still has [matchCount] step-definition candidates
     * after scope filtering. No-op unless AUTO scope is enabled, [matchCount] > 1,
     * and the balloon hasn't been shown for this project yet.
     */
    fun maybeAdviseAboutCucumberScope(project: Project, matchCount: Int) {
        if (matchCount <= 1) return
        val state = project.getService(TzPersistenceStateService::class.java)
        if (state.stepScope != StepScopeMode.AUTO) return
        if (state.stepScopeBalloonShown) return

        // Mark immediately to avoid races when several lookups fire on opening a file.
        state.stepScopeBalloonShown = true

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            project.notification(
                """
                <html>
                A Gherkin step still matches several step definitions in your project.<br/>
                Drop a <code>.cucumber-scope</code> empty file at the root of an app folder to
                limit step indexing to that folder only.<br/>
                <br/>
                Auto-detected anchors: <code>.cucumber-scope</code>, <code>package.json</code>,
                <code>pom.xml</code>, <code>build.gradle</code>.
                </html>
                """.trimIndent(),
                NotificationType.INFORMATION
            )
        }, com.intellij.openapi.application.ModalityState.nonModal())
    }
}
