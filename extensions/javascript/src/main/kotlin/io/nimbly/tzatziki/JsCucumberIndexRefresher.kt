/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptExtension

/**
 * Heals the cucumber-javascript step-def stub index (`$cucumberJSSteps` user
 * string on `JSImplicitElementImpl`) when it comes up empty.
 *
 * Symptom: after the Cucumber+ plugin jar is swapped under the IDE — a plugin
 * UPDATE for end users, or a `runIde` rebuild during development — the
 * cucumber-js stub cache survives but `CucumberJavaScriptExtension.loadStepsFor()`
 * returns an empty list until `File → Invalidate Caches` is run. With no step
 * defs resolved, none of Cucumber+'s JS/TS features (breakpoint sync, gutter
 * marker, test-tree decoration, example filtering) work. This is NOT dev-only:
 * the classloader swap on a Marketplace update reproduces it.
 *
 * Strategy — **probe, then heal only if broken** (no blanket reindex on every
 * project open):
 *  1. Wait for smart mode (indexing finished) so an empty result is meaningful.
 *  2. Collect the project's step-def files (cheap, skips node_modules/build/…).
 *  3. Probe ONE of them via `loadStepsFor`. If it returns step defs, the index
 *     is healthy → do nothing.
 *  4. Only if the probe is empty (stale stub cache) request a reindex of the
 *     step-def files, which re-runs the cucumber-js stub processor.
 */
class JsCucumberIndexRefresher : ProjectActivity {

    private val log = Logger.getInstance(JsCucumberIndexRefresher::class.java)

    override suspend fun execute(project: Project) {
        val root = project.guessProjectDir() ?: return

        DumbService.getInstance(project).runWhenSmart {
            val targets = collectStepDefFiles(root)
            if (targets.isEmpty()) return@runWhenSmart

            // Health probe: if the cucumber-js index already resolves step defs
            // from a representative file, the stub cache is fine — do nothing.
            if (indexLooksHealthy(project, targets)) {
                log.info("C+ JsCucumberIndexRefresher: cucumber-js stub index healthy — no reindex needed")
                return@runWhenSmart
            }

            val fbi = FileBasedIndex.getInstance()
            targets.forEach { fbi.requestReindex(it) }
            log.info("C+ JsCucumberIndexRefresher: stale stub index — reindexed ${targets.size} step-def file(s) for '${project.name}'")
        }
    }

    /** True if [loadStepsFor] resolves at least one step def from any of the
     *  candidate files — meaning the cucumber-js stub index is usable. */
    private fun indexLooksHealthy(project: Project, candidates: List<VirtualFile>): Boolean {
        return ReadAction.compute<Boolean, RuntimeException> {
            val ext = CucumberJavaScriptExtension()
            val psiManager = PsiManager.getInstance(project)
            // Probe a few files (not just the first) in case one happens to have
            // no top-level step defs; bail out as soon as one resolves.
            candidates.asSequence().take(5).any { vfile ->
                val psiFile = psiManager.findFile(vfile) ?: return@any false
                val module = ModuleUtilCore.findModuleForFile(vfile, project)
                runCatching { ext.loadStepsFor(psiFile, module).isNotEmpty() }.getOrDefault(false)
            }
        }
    }

    private fun collectStepDefFiles(root: VirtualFile): List<VirtualFile> {
        val targets = mutableListOf<VirtualFile>()
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    val n = file.name
                    if (n == "node_modules" || n == "build" || n == "out"
                        || n == "dist" || n == ".gradle" || n.startsWith(".")
                    ) return false
                    return true
                }
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
        return targets
    }
}
