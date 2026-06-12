/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.rename

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.refactoring.rename.RenameHandler
import java.util.concurrent.atomic.AtomicBoolean

/** FQN of cucumber's step rename handler — see ReflectionApiTest for the guard against renames. */
const val CUCUMBER_STEP_RENAME_HANDLER = "org.jetbrains.plugins.cucumber.psi.refactoring.rename.GherkinStepRenameHandler"

/**
 * Removes cucumber's `GherkinStepRenameHandler` from the (application-level) `RenameHandler` EP so
 * our [TzGherkinStepRenameHandler] becomes the SOLE rename handler for Gherkin steps — otherwise
 * the IDE shows a "choose a handler" popup (both are available).
 *
 * Why removal and not ordering: cucumber registers its handler WITHOUT an `order` (so we can't be
 * preferred by order alone — two available handlers always trigger the chooser) AND its
 * `GherkinStepRenameProcessor` with `order="first"` loaded before us (so the processor path can't
 * win either). Dropping the handler is the only way to route Shift+F6 to our table / parameter /
 * doc-string-safe rename. The class FQN is guarded by ReflectionApiTest.
 */
class TzRenameHandlerStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!done.compareAndSet(false, true)) return
        runCatching {
            val point = RenameHandler.EP_NAME.point
            val cucumber = point.extensionList.firstOrNull { it.javaClass.name == CUCUMBER_STEP_RENAME_HANDLER }
            if (cucumber != null) point.unregisterExtension(cucumber.javaClass)
        }.onFailure {
            thisLogger().warn("Cucumber+ : could not drop cucumber's $CUCUMBER_STEP_RENAME_HANDLER (rename chooser may appear)", it)
        }
    }

    companion object {
        // App-level EP → do it once for the whole IDE session.
        private val done = AtomicBoolean(false)
    }
}
