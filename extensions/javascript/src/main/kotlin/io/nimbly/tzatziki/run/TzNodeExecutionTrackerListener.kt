/*
 * CUCUMBER +
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.run

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.util.PsiModificationTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Populates the project's `cucumberExecutionTracker` from SMTRunner events so
 * the JS / TS BP-filter path (TzRunNodeListener) knows which step / example row
 * is currently executing.
 *
 * Background: [io.nimbly.tzatziki.run.TzExecutionCucumberListener] populates
 * the tracker by parsing `[testStarted ...]` / `[testSuiteStarted ...]` TeamCity
 * messages it sees on the run process's stdout. That works for cucumber-jvm,
 * which prints them straight to the console — but the JS test framework
 * (cucumber-javascript run config) consumes those messages *internally* before
 * they reach the raw `onTextAvailable` channel our ProcessListener listens to.
 * Result: with the JS runner the tracker stays empty and BP filtering can't
 * decide whether to resume.
 *
 * Fix: subscribe to [SMTRunnerEventsListener] (which DOES fire for cucumber-js)
 * and mirror the same fields. Idempotent w.r.t. the JVM path — both update the
 * tracker with identical values when they both fire.
 */
class TzNodeExecutionTrackerListener : ProjectActivity {

    private val log = logger<TzNodeExecutionTrackerListener>()
    private val locationRegex = Regex("(.*\\.feature):(\\d+)$")

    /**
     * Per-(vfile URL, line) cache for the "is this line a data row inside an
     * Examples block?" PSI walk. Without this we run a fresh ReadAction +
     * PsiTreeUtil walk on EVERY SMTRunner event, and cucumber-js emits N+1
     * suite / test events per scenario × number of examples. On a feature with
     * a dozen scenarios + an outline with 50 examples that's hundreds of read
     * actions per debug run.
     *
     * Cache key includes the project's PsiModificationTracker count so edits
     * to the .feature invalidate the cache automatically.
     */
    private data class DataRowKey(val vfileUrl: String, val line: Int)
    private data class DataRowEntry(val modCount: Long, val isDataRow: Boolean)
    private val dataRowCache = ConcurrentHashMap<DataRowKey, DataRowEntry>()

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            SMTRunnerEventsListener.TEST_STATUS,
            object : SMTRunnerEventsAdapter() {
                override fun onTestStarted(test: SMTestProxy) = update(project, test)
                override fun onSuiteStarted(suite: SMTestProxy) = update(project, suite)
            },
        )
        log.info("C+ TzNodeExecutionTrackerListener subscribed for project '${project.name}'")
    }

    private fun update(project: Project, proxy: SMTestProxy) {
        val locationUrl = proxy.locationUrl
        if (locationUrl == null) {
            log.info("C+ tracker-update SKIP — locationUrl null for proxy='${proxy.name}'")
            return
        }
        val match = locationRegex.matchEntire(locationUrl)
        if (match == null) {
            log.info("C+ tracker-update SKIP — locationUrl doesn't match `*.feature:N` regex: '$locationUrl' (proxy='${proxy.name}')")
            return
        }
        val line = match.groupValues[2].toIntOrNull() ?: return

        // Resolve the .feature file via the proxy's own `getLocation()` — the
        // cucumber-js test framework emits PROJECT-RELATIVE `file:///` URLs
        // (e.g. `file:///features/calculator.feature:12`), so naïve path
        // stripping leaves us with `/features/calculator.feature` which doesn't
        // match anything on disk. `getLocation()` uses the same VFS resolver
        // that powers `proxy.navigate()`, so it works for both relative and
        // absolute hints.
        val resolved = com.intellij.openapi.application.runReadAction<com.intellij.openapi.vfs.VirtualFile?> {
            runCatching {
                proxy.getLocation(project, com.intellij.psi.search.GlobalSearchScope.allScope(project))?.virtualFile
            }.getOrNull()
        }
        if (resolved == null) {
            log.info("C+ tracker-update SKIP — proxy.getLocation null for '$locationUrl' (proxy='${proxy.name}')")
            return
        }
        val absolutePath = resolved.path

        val tracker = project.cucumberExecutionTracker()
        val name = proxy.name

        // cucumber-js doesn't emit `Example #N` prefixed events — every iteration
        // of a Scenario Outline gets `Scenario: <outline name>` and `Step: ...`
        // events whose locationUrl points to the DATA ROW line (not the outline
        // step line, not the data row column header). So we can't rely on the
        // event NAME to know we're in an outline iteration. PSI is the only
        // reliable signal: if the line resolves to a GherkinTableRow inside an
        // Examples block, treat the line as `exampleLine`; otherwise treat it
        // as a step / scenario line and store in `lineNumber`.
        val isDataRowLine = isDataRowLineCached(project, resolved, line)

        tracker.featurePath = absolutePath
        val isExample = isDataRowLine || name.startsWith("Example #")
        if (isExample) {
            tracker.exampleLine = line
            log.info("C+ tracker-update example proxy='$name' featurePath='$absolutePath' exampleLine=$line (1-based)")
        } else {
            tracker.lineNumber = line - 1
            log.info("C+ tracker-update step proxy='$name' featurePath='$absolutePath' line=${line - 1} (0-based)")
        }

        // Drive the gutter progression bar — the stdout-based path in
        // TzExecutionCucumberListener never fires for cucumber-js, so we paint
        // it here from the SMTRunner events instead. Anchor = the enclosing
        // scenario / outline header line; end = the current step / example-row
        // line. Only paint for the leaf "execution" events (step & example-row);
        // the scenario-header event would draw a useless single-line bar.
        if (name.startsWith("Step: ") || isExample) {
            val headerLine0 = scenarioHeaderLine0(project, resolved, line - 1)
            if (headerLine0 != null) {
                paintCucumberProgression(
                    project = project,
                    vfile = resolved,
                    lineStart = headerLine0,
                    lineEnd = line - 1,
                    isExample = isExample,
                )
            }
        }
    }

    /**
     * 0-based start line of the [org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder]
     * (scenario / outline / background / rule) enclosing the given 0-based [line].
     * Used as the top anchor of the progression bar. Null when the line isn't
     * inside a holder.
     */
    private fun scenarioHeaderLine0(
        project: Project,
        vfile: com.intellij.openapi.vfs.VirtualFile,
        line: Int,
    ): Int? {
        return com.intellij.openapi.application.ReadAction.compute<Int?, RuntimeException> {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vfile)
                ?: return@compute null
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vfile)
                ?: return@compute null
            val lineIdx = line.coerceIn(0, doc.lineCount - 1)
            val lineStart = doc.getLineStartOffset(lineIdx)
            val lineEnd = doc.getLineEndOffset(lineIdx)
            var probe = lineStart
            while (probe < lineEnd && doc.charsSequence[probe].isWhitespace()) probe++
            val element = psiFile.findElementAt(probe) ?: return@compute null
            val holder = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                element, org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder::class.java, false,
            ) ?: return@compute null
            doc.getLineNumber(holder.textRange.startOffset)
        }
    }

    private fun isDataRowLineCached(
        project: Project,
        vfile: com.intellij.openapi.vfs.VirtualFile,
        line: Int,
    ): Boolean {
        val modCount = PsiModificationTracker.getInstance(project).modificationCount
        val key = DataRowKey(vfile.url, line)
        dataRowCache[key]?.let { if (it.modCount == modCount) return it.isDataRow }

        val isDataRow = com.intellij.openapi.application.ReadAction.compute<Boolean, RuntimeException> {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vfile)
                ?: return@compute false
            val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vfile)
                ?: return@compute false
            val lineIdx = (line - 1).coerceIn(0, doc.lineCount - 1)
            val lineStart = doc.getLineStartOffset(lineIdx)
            val lineEnd = doc.getLineEndOffset(lineIdx)
            var probe = lineStart
            while (probe < lineEnd && doc.charsSequence[probe].isWhitespace()) probe++
            val element = psiFile.findElementAt(probe) ?: return@compute false
            val row = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                element, org.jetbrains.plugins.cucumber.psi.GherkinTableRow::class.java,
            ) ?: return@compute false
            if (row is org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl) return@compute false
            com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                row, org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock::class.java,
            ) != null
        }

        dataRowCache[key] = DataRowEntry(modCount, isDataRow)
        if (dataRowCache.size > 512) {
            // Bound size — drop arbitrary entries (next read repopulates).
            val excess = dataRowCache.size - 512
            dataRowCache.keys.take(excess).forEach { dataRowCache.remove(it) }
        }
        return isDataRow
    }
}
