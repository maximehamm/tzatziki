/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.indexing.FileBasedIndex

/**
 * Dev-time / sandbox-only annoyance fix: the cucumber-javascript plugin builds its
 * step-def stub index (`$cucumberJSSteps` user string on `JSImplicitElementImpl`) at
 * file parse time. When we rebuild plugin-tzatziki and the sandbox restarts, the
 * cached stubs survive but get loaded by a different classloader than the
 * cucumber-js plugin uses, so `CucumberJavaScriptExtension.loadStepsFor()` returns
 * an empty list until the user triggers `File → Invalidate Caches and Restart`.
 *
 * Workaround: at project startup, request a re-index of every JS / TS file under
 * the project so the cucumber-js plugin re-runs its stub processor with the live
 * classloader. The Gherkin↔step-def navigation works on first try afterwards —
 * no more manual invalidation needed every sandbox restart.
 *
 * This is purely cosmetic in production (an end user's IDE never rebuilds this
 * plugin's jar between runs), but makes the inner-dev loop bearable.
 */
class JsCucumberIndexRefresher : ProjectActivity {

    private val log = Logger.getInstance(JsCucumberIndexRefresher::class.java)

    override suspend fun execute(project: Project) {
        val root = project.guessProjectDir() ?: return
        val targets = mutableListOf<VirtualFile>()
        // We only need cucumber-js step-def files reindexed — the stub the
        // cucumber-javascript plugin builds (`$cucumberJSSteps`) lives in the
        // files that call `Given(...) / When(...) / Then(...)` at top level.
        // Filter to directories named `step_definitions` (the convention used
        // by cucumber-js) so we don't reindex an entire monorepo just to
        // refresh a handful of step-def files.
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    val n = file.name
                    // Hard-skip noise — these are project-wide directories that
                    // never contain step-def files and are huge.
                    if (n == "node_modules" || n == "build" || n == "out"
                        || n == "dist" || n == ".gradle" || n.startsWith(".")
                    ) return false
                    return true
                }
                // Heuristic: only step-def files. Two ways to spot them:
                //  - placed under a `step_definitions` / `step-defs` / `steps` directory
                //    (the conventional cucumber-js layout)
                //  - file name ending with `.steps.js` / `.steps.ts` (common
                //    alternative seen in real-world projects)
                val e = file.extension?.lowercase()
                val isJsOrTs = e == "js" || e == "jsx" || e == "mjs" || e == "cjs"
                    || e == "ts" || e == "tsx"
                if (!isJsOrTs) return true
                val path = file.path
                val isStepDefFile =
                    path.contains("/step_definitions/") ||
                    path.contains("/step-defs/") ||
                    path.contains("/steps/") ||
                    file.nameWithoutExtension.endsWith(".steps") ||
                    file.nameWithoutExtension.endsWith(".steps.spec")
                if (isStepDefFile) targets.add(file)
                return true
            }
        })
        if (targets.isEmpty()) {
            log.info("C+ JsCucumberIndexRefresher: no step-def files found under ${root.path}")
            return
        }
        val fbi = FileBasedIndex.getInstance()
        targets.forEach { fbi.requestReindex(it) }
        log.info("C+ JsCucumberIndexRefresher: requested reindex of ${targets.size} step-def file(s) for project '${project.name}'")
    }
}
