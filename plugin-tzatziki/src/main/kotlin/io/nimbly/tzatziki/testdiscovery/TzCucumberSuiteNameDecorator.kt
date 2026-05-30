/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.testdiscovery

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl

/**
 * Structured decoration attached to a Cucumber+ outermost suite proxy via
 * [SMTestProxy.putUserData] — consumed by [TzCucumberTreeStyledRenderer] to render
 * multi-fragment styled cell text (file name in grey, primary header in bold,
 * secondaries in grey italic).
 */
data class CucumberSuiteDecoration(
    val fileName: String,
    val primary: String?,
    val secondaries: List<String>
)

val CUCUMBER_DECORATION_KEY: Key<CucumberSuiteDecoration> =
    Key.create("tzatziki.cucumber.suite.decoration")
val CUCUMBER_WRAPPER_KEY: Key<Boolean> =
    Key.create("tzatziki.cucumber.suite.wrapper")
/** Pre-formatted tag string (e.g. " @Production @Chrome") for any scenario-level suite. */
val CUCUMBER_TAGS_KEY: Key<String> =
    Key.create("tzatziki.cucumber.suite.tags")
/** Ordered (header, value) pairs of a single `Examples:` row under a Scenario
 *  Outline — e.g. `[("Age","22"), ("Score","75"), ("Prenom","Clara")]`.
 *  Stored as pairs (not a pre-formatted string) so the renderer can italicise
 *  each header and keep its value in plain grey. */
val CUCUMBER_EXAMPLE_KEY: Key<List<Pair<String, String>>> =
    Key.create("tzatziki.cucumber.suite.example")
/** 1-based position of an example data row within its `Examples:` block. Used
 *  to prefix the node with `#1`, `#2`, … so the otherwise-identically-named
 *  Scenario Outline iterations (cucumber-js repeats the outline name verbatim
 *  for each row) are visually distinguishable — the lightweight equivalent of
 *  cucumber-jvm's intermediate `Example #N` tree nodes. */
val CUCUMBER_EXAMPLE_INDEX_KEY: Key<Int> =
    Key.create("tzatziki.cucumber.suite.exampleIndex")
/** `true` when the node's location line resolves to a Gherkin STEP (not a
 *  scenario / outline header). Lets the styled renderer tell steps from
 *  scenario suites reliably — the previous `proxy.children.none { !it.isLeaf }`
 *  heuristic broke on cucumber-js, where a scenario's child steps are leaf
 *  tests (no child suites) so the scenario itself was misread as a step. */
val CUCUMBER_IS_STEP_KEY: Key<Boolean> =
    Key.create("tzatziki.cucumber.suite.isStep")

/**
 * Decorates the outermost Cucumber suite node in the Run / Debug tool window so the user
 * can tell features apart at a glance:
 *
 *   Test Results
 *     └─ Business Need: toto  /  France.feature
 *         └─ Scenario: …
 *
 * The platform fires several events along a suite's lifecycle (`onSuiteTreeNodeAdded`,
 * `onSuiteTreeStarted`, `onSuiteStarted`). We hook all of them: cucumber-jvm doesn't
 * use the pre-tree mechanism, so `onSuiteStarted` is normally the one that fires, but
 * IntelliJ-driven runners (e.g. Run an individual scenario) emit the tree events first.
 *
 * Idempotent: a marker suffix prevents double-decorating on replayed events.
 */
class TzCucumberSuiteNameDecorator : ProjectActivity {

    override suspend fun execute(project: Project) {
        val adapter = object : SMTRunnerEventsAdapter() {
            override fun onSuiteStarted(suite: SMTestProxy) = decorate(suite, project, "started")
            override fun onSuiteTreeNodeAdded(suite: SMTestProxy) = decorate(suite, project, "treeAdded")
            override fun onSuiteTreeStarted(suite: SMTestProxy) = decorate(suite, project, "treeStarted")
        }
        // Subscribe BOTH to the project bus (standard) and the application bus — some
        // test runners (notably some Windows+WSL cucumber-jvm setups reported by users)
        // publish SMTRunner events on the application bus only, so the project-only
        // subscription would silently miss every event and we'd never decorate.
        project.messageBus.connect().subscribe(SMTRunnerEventsListener.TEST_STATUS, adapter)
        com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect(project)
            .subscribe(SMTRunnerEventsListener.TEST_STATUS, adapter)
        LOG.info("C+ TzCucumberSuiteNameDecorator subscribed (project + application bus) for project ${project.name}")
    }

    /** Public — also invoked lazily by [TzCucumberTreeStyledRenderer] as a safety net
     *  when the SMTRunner events never fire on the current setup. */
    fun decorate(suite: SMTestProxy, project: Project, phase: String) {
        if (!TOGGLE_CUCUMBER_PL) return

        // The cucumber-jvm tree looks like:
        //   Test Results
        //     └─ Cucumber                    ← wrapper category → rename "Cucumber+"
        //         └─ Business Need: toto     ← OUTERMOST FEATURE → append file name
        //             └─ Scenario: …
        //                 └─ Given …

        // 1) Top-level "Cucumber" wrapper → brand it "Cucumber+".
        if (suite.name == "Cucumber" && suite.parent?.parent == null) {
            if (suite.presentableName != "Cucumber+") {
                suite.setPresentableName("Cucumber+")
                suite.putUserData(CUCUMBER_WRAPPER_KEY, true)
                LOG.info("C+ decorate[$phase] wrapper 'Cucumber' -> 'Cucumber+'")
            }
            return
        }

        // 2) Outermost feature-level suite — it carries a `.feature` locationUrl AND its
        //    parent does NOT (so we skip step-level suites that also point to .feature).
        val locationUrl = suite.locationUrl ?: return
        val fileName = featureFileNameOf(locationUrl) ?: return
        val parentUrl = suite.parent?.locationUrl

        // 2bis) Scenario-level suite (parent also has a .feature URL): not the outermost,
        // but a child carrying tags we want to surface. Compute tags via PSI once, store
        // for the styled renderer, then bail — no name decoration needed at this level.
        if (parentUrl != null && featureFileNameOf(parentUrl) != null) {
            // cucumber-js prefixes its SMTRunner suite / test nodes with a fixed
            // English label ("Scenario: …", "Scenario Outline: …", "Step: …")
            // that cucumber-jvm never emits. Strip it so the JS / TS test tree
            // reads consistently with the Java one. No-op on cucumber-jvm names.
            val prefixKind = stripRunnerPrefix(suite)
            // Reliable scenario/step classification. cucumber-js outline-iteration
            // STEP nodes carry their location at the DATA-ROW line (not the step
            // line), so a PSI check there would see a GherkinTableRow and wrongly
            // treat the step as a scenario/example. The runner's name prefix
            // ("Step: " vs "Scenario: ") is the trustworthy signal; fall back to
            // PSI only for cucumber-jvm (no prefix). Cached in CUCUMBER_IS_STEP_KEY.
            val isStep = suite.getUserData(CUCUMBER_IS_STEP_KEY) ?: run {
                val v = when (prefixKind) {
                    RunnerNodeKind.STEP -> true
                    RunnerNodeKind.SCENARIO -> false
                    RunnerNodeKind.NONE -> isStepLine(project, suite, locationUrl)
                }
                suite.putUserData(CUCUMBER_IS_STEP_KEY, v)
                v
            }
            // Tags belong to the SCENARIO / OUTLINE header only — never to a step.
            if (!isStep && suite.getUserData(CUCUMBER_TAGS_KEY) == null) {
                val tags = readScenarioTags(project, suite, locationUrl)
                if (tags.isNotEmpty()) {
                    suite.putUserData(CUCUMBER_TAGS_KEY, tags.joinToString(" ", prefix = " "))
                    LOG.info("C+ decorate[$phase] scenario '${suite.name}' loc='$locationUrl' tags=$tags")
                }
            }
            // Scenario Outline iteration node: surface the `#N` ordinal + example
            // data. Only on the SCENARIO iteration node, NOT on its step children
            // (which share the same data-row location in cucumber-js).
            if (!isStep && suite.getUserData(CUCUMBER_EXAMPLE_KEY) == null) {
                val example = readExampleRowData(project, suite, locationUrl)
                if (example != null) {
                    suite.putUserData(CUCUMBER_EXAMPLE_KEY, example.cells)
                    suite.putUserData(CUCUMBER_EXAMPLE_INDEX_KEY, example.index)
                }
            }
            return
        }

        val current = suite.presentableName ?: suite.name
        if (current.startsWith(fileName)) return  // already decorated (replay safety)
        // Read the .feature PSI to surface every keyword name in source order
        // ((keywordText, name) pairs). Display rules:
        //   - 0 pairs        → fall back to `File.feature  /  <current>`
        //   - 1 pair, kw is `Business Need` / `Ability`
        //                    → `File.feature [name]`              (alternative header only)
        //   - 1 pair, kw is `Feature` (or any other / localised)
        //                    → `File.feature  /  name`
        //   - n pairs        → `File.feature  /  primary [secondary, …]`
        //                       primary = LAST in source order.
        val pairs = readFeatureKeywordPairs(project, suite, locationUrl)
        // Decompose into structured parts (fileName / primary / secondaries) so the
        // styled cell renderer can format each fragment with its own SimpleTextAttributes.
        // We ALSO build a fallback plain-text presentableName in case the renderer wrapper
        // is not yet installed (e.g. tree shown before [TzCucumberTreeStyledRenderer] hooks).
        val decoration: CucumberSuiteDecoration
        val plain: String
        when {
            pairs.isEmpty() -> {
                decoration = CucumberSuiteDecoration(fileName, current, emptyList())
                plain = "$fileName$MARKER$current"
            }
            pairs.size == 1 -> {
                val (kw, name) = pairs.first()
                if (kw.equals("Business Need", true) || kw.equals("Ability", true)) {
                    decoration = CucumberSuiteDecoration(fileName, null, listOf(name))
                    plain = "$fileName [$name]"
                } else {
                    decoration = CucumberSuiteDecoration(fileName, name, emptyList())
                    plain = "$fileName$MARKER$name"
                }
            }
            else -> {
                val primary = pairs.last().second
                val secondaries = pairs.dropLast(1).map { it.second }
                decoration = CucumberSuiteDecoration(fileName, primary, secondaries)
                plain = "$fileName$MARKER$primary [${secondaries.joinToString(", ")}]"
            }
        }
        suite.putUserData(CUCUMBER_DECORATION_KEY, decoration)
        // Feature-level tags (e.g. `@global` above `Feature:`) — surface them too.
        if (suite.getUserData(CUCUMBER_TAGS_KEY) == null) {
            val featureTags = readScenarioTags(project, suite, locationUrl)
            if (featureTags.isNotEmpty()) {
                suite.putUserData(CUCUMBER_TAGS_KEY, featureTags.joinToString(" ", prefix = " "))
            }
        }
        suite.setPresentableName(plain)
        LOG.info("C+ decorate[$phase] '${suite.name}' loc='$locationUrl' -> file='${decoration.fileName}' primary='${decoration.primary}' sec=${decoration.secondaries}")
    }

    /**
     * Returns the (keyword, name) pairs following each FEATURE_KEYWORD in the file, in
     * source order. We walk the WHOLE `PsiFile` (not just the first `GherkinFeature`)
     * because the cucumber-plugin parses a second `Feature:`/`Ability:` next to a
     * `Business Need:` differently depending on the IntelliJ build: sometimes nested
     * inside the first GherkinFeatureHeader, sometimes as a second GherkinFeature
     * sibling. Walking from the file root catches both layouts.
     */
    private fun readFeatureKeywordPairs(project: Project, suite: SMTestProxy, locationUrl: String): List<Pair<String, String>> {
        val vfile = resolveFeatureVFile(project, suite, locationUrl) ?: run {
            LOG.warn("C+ readFeatureKeywordPairs unable to resolve VFS for locationUrl='$locationUrl'")
            return emptyList()
        }
        return runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@runReadAction emptyList()
            val pairs = mutableListOf<Pair<String, String>>()
            var pendingKeyword: String? = null
            fun walk(n: com.intellij.lang.ASTNode) {
                when (n.elementType) {
                    GherkinTokenTypes.FEATURE_KEYWORD -> pendingKeyword = n.text.trim()
                    GherkinTokenTypes.TEXT -> {
                        val kw = pendingKeyword
                        if (kw != null) {
                            val name = n.text.trim()
                            if (name.isNotEmpty()) pairs += kw to name
                            pendingKeyword = null
                        }
                    }
                    else -> { /* skip COLON / whitespace / non-leaf composites we don't care about */ }
                }
                var c = n.firstChildNode
                while (c != null) {
                    walk(c)
                    c = c.treeNext
                }
            }
            walk(psiFile.node)
            pairs
        }
    }

    /**
     * Read the `@Production @Chrome …` tags of the [GherkinStepsHolder] sitting at the
     * line encoded in [locationUrl] (`file://…/Foo.feature:NN`). Returns the tag texts
     * including the leading `@`, in source order. Empty list if the holder has no tags
     * or the URL doesn't resolve. Runs inside a read action — safe to call from any thread.
     */
    private fun readScenarioTags(project: Project, suite: SMTestProxy, locationUrl: String): List<String> {
        // The Feature-level node's locationUrl has NO `:NN` suffix
        // (`file:///…/Foo.feature`) — in that case resolve the tags off the
        // GherkinFeature directly. Scenario / step nodes carry a `:NN` line.
        val lineNumber = Regex("(.*\\.feature):(\\d+)$").matchEntire(locationUrl)
            ?.groupValues?.get(2)?.toIntOrNull()
        val vfile = resolveFeatureVFile(project, suite, locationUrl) ?: return emptyList()
        return runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@runReadAction emptyList()
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vfile)
                ?: return@runReadAction emptyList()
            val holder = if (lineNumber == null) {
                // Feature node: anchor on the GherkinFeature itself.
                PsiTreeUtil.findChildOfType(psiFile, GherkinFeature::class.java)
                    ?: return@runReadAction emptyList()
            } else {
                val lineIdx = (lineNumber - 1).coerceIn(0, document.lineCount - 1)
                val lineStart = document.getLineStartOffset(lineIdx)
                val lineEnd = document.getLineEndOffset(lineIdx)
                // Skip leading whitespace — `findElementAt(lineStart)` would land on the
                // indentation PsiWhiteSpace, whose parent is the ENCLOSING element (e.g. the
                // GherkinFeature for a scenario line), not the scenario itself. That gave us
                // false-positive Feature-level tags on every scenario.
                val firstNonWs = document.charsSequence
                    .subSequence(lineStart, lineEnd)
                    .indexOfFirst { !it.isWhitespace() }
                val offset = lineStart + firstNonWs.coerceAtLeast(0)
                val element = psiFile.findElementAt(offset)
                element?.let { PsiTreeUtil.getParentOfType(it, GherkinStepsHolder::class.java, false) }
                    ?: PsiTreeUtil.findChildOfType(psiFile, GherkinFeature::class.java)
                    ?: return@runReadAction emptyList()
            }

            // Collect tags from BOTH locations regardless of holder type:
            //   1) child GherkinTag of the holder (typical for scenario / background / rule)
            //   2) preceding-sibling GherkinTag (typical for Feature-level tags written
            //      above the `Feature:` keyword — they sit at the file root)
            val tags = mutableListOf<String>()
            PsiTreeUtil.getChildrenOfType(holder, GherkinTag::class.java)
                ?.forEach { tags += it.text.trim() }
            val preceding = mutableListOf<String>()
            var prev = holder.prevSibling
            while (prev != null) {
                when (prev) {
                    is GherkinTag -> preceding.add(0, prev.text.trim())
                    is com.intellij.psi.PsiWhiteSpace, is com.intellij.psi.PsiComment -> { /* skip */ }
                    else -> break
                }
                prev = prev.prevSibling
            }
            (preceding + tags).filter { it.isNotEmpty() }
        }
    }

    /**
     * `true` when [locationUrl]'s line resolves to a Gherkin STEP (Given / When /
     * Then / And / But). Used to tell step nodes from scenario / outline header
     * nodes reliably — the cucumber-js test tree makes a scenario's child steps
     * leaf tests, so the old "has no child suites" heuristic mis-classified the
     * scenario itself as a step. Runs in a read action; safe off-EDT.
     */
    private fun isStepLine(project: Project, suite: SMTestProxy, locationUrl: String): Boolean {
        val match = Regex("(.*\\.feature):(\\d+)$").matchEntire(locationUrl) ?: return false
        val lineNumber = match.groupValues[2].toIntOrNull() ?: return false
        val vfile = resolveFeatureVFile(project, suite, locationUrl) ?: return false
        return runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@runReadAction false
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vfile)
                ?: return@runReadAction false
            val lineIdx = (lineNumber - 1).coerceIn(0, document.lineCount - 1)
            val lineStart = document.getLineStartOffset(lineIdx)
            val lineEnd = document.getLineEndOffset(lineIdx)
            val firstNonWs = document.charsSequence
                .subSequence(lineStart, lineEnd)
                .indexOfFirst { !it.isWhitespace() }
            val offset = lineStart + firstNonWs.coerceAtLeast(0)
            val element = psiFile.findElementAt(offset) ?: return@runReadAction false
            PsiTreeUtil.getParentOfType(
                element, org.jetbrains.plugins.cucumber.psi.GherkinStep::class.java, false,
            ) != null
        }
    }

    /**
     * If [locationUrl] points to a data row inside a `Scenarios:` / `Examples:` table
     * (i.e. the suite is an "Example #N" child of a Scenario Outline), returns a
     * `Header1: cell1, Header2: cell2, …` string. Empty cells and ones whose header
     * is empty are skipped. Returns null when the line is the header row or doesn't
     * sit inside an Examples table.
     */
    /** Result of [readExampleRowData]: the 1-based data-row index within the
     *  Examples block + the (header, value) cell pairs. */
    private data class ExampleRowInfo(val index: Int, val cells: List<Pair<String, String>>)

    private fun readExampleRowData(project: Project, suite: SMTestProxy, locationUrl: String): ExampleRowInfo? {
        val match = Regex("(.*\\.feature):(\\d+)$").matchEntire(locationUrl) ?: return null
        val lineNumber = match.groupValues[2].toIntOrNull() ?: return null
        val vfile = resolveFeatureVFile(project, suite, locationUrl) ?: return null
        return runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@runReadAction null
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vfile)
                ?: return@runReadAction null
            val lineIdx = (lineNumber - 1).coerceIn(0, document.lineCount - 1)
            val lineStart = document.getLineStartOffset(lineIdx)
            val lineEnd = document.getLineEndOffset(lineIdx)
            val firstNonWs = document.charsSequence
                .subSequence(lineStart, lineEnd)
                .indexOfFirst { !it.isWhitespace() }
            val offset = lineStart + firstNonWs.coerceAtLeast(0)
            val element = psiFile.findElementAt(offset) ?: return@runReadAction null
            val row = PsiTreeUtil.getParentOfType(element, GherkinTableRow::class.java, false)
                ?: return@runReadAction null
            if (row is GherkinTableHeaderRowImpl) return@runReadAction null
            val table = PsiTreeUtil.getParentOfType(row, GherkinTable::class.java)
                ?: return@runReadAction null
            val allRows = PsiTreeUtil.getChildrenOfTypeAsList(table, GherkinTableRow::class.java)
            // Header = the first row inside the table (typically GherkinTableHeaderRowImpl).
            val header = allRows.firstOrNull() ?: return@runReadAction null
            // 1-based index of this row among the DATA rows (everything after the
            // header). `allRows.indexOf(row)` is 0-based incl. header at 0, so the
            // first data row (index 1) maps to #1.
            val rowIndex = allRows.indexOf(row)
            if (rowIndex < 1) return@runReadAction null
            val headerCells = header.psiCells.map { it.text.trim() }
            val dataCells = row.psiCells.map { it.text.trim() }
            if (headerCells.isEmpty() || dataCells.isEmpty()) return@runReadAction null
            val cells = headerCells.zip(dataCells) { h, d -> h to d }
                .filter { (h, _) -> h.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?: return@runReadAction null
            ExampleRowInfo(index = rowIndex, cells = cells)
        }
    }

    /**
     * Robust VFS resolution for a cucumber-jvm test suite.
     *
     * Strategy in order (most reliable first):
     *   0. `proxy.getLocation(project, allScope).virtualFile` — the SAME mechanism the
     *      platform uses for `proxy.navigate()`, so it transparently handles
     *      Windows-with-WSL setups where the test emits `file:///home/…` URLs that
     *      IntelliJ stores internally as `file:////wsl.localhost/Ubuntu/home/…` (or
     *      similar). Falls back to URL parsing only when getLocation returns null.
     *   1. `VirtualFileManager.findFileByUrl(url)` — direct VFS lookup.
     *   2. `URI.path` parsing → Windows `/C:/…` → `C:/…` normalisation.
     *   3. Brute strip.
     */
    private fun resolveFeatureVFile(
        project: Project,
        suite: SMTestProxy,
        locationUrl: String
    ): com.intellij.openapi.vfs.VirtualFile? {
        // Prefer the platform's own resolver — works on Windows+WSL where URL string
        // parsing alone fails.
        runReadAction {
            runCatching {
                suite.getLocation(project, com.intellij.psi.search.GlobalSearchScope.allScope(project))
                    ?.virtualFile
            }.getOrNull()
        }?.let { return it }

        val cleanUrl = if (locationUrl.matches(Regex(".*:\\d+$"))) locationUrl.substringBeforeLast(':') else locationUrl
        val vfm = VirtualFileManager.getInstance()
        val lfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()

        vfm.findFileByUrl(cleanUrl)?.let { return it }

        runCatching {
            val uri = java.net.URI(cleanUrl)
            val uriPath = uri.path ?: return@runCatching null
            val normalized = if (uriPath.length > 2 && uriPath[0] == '/' && uriPath[2] == ':') {
                uriPath.drop(1)
            } else uriPath
            lfs.findFileByPath(normalized)
        }.getOrNull()?.let { return it }

        val brute = cleanUrl
            .removePrefix("file:///")
            .removePrefix("file://")
            .let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8) }.getOrDefault(it) }
        lfs.findFileByPath(brute)?.let { return it }

        LOG.warn("C+ resolveFeatureVFile FAILED for cleanUrl='$cleanUrl'")
        return null
    }

    /**
     * Removes the `Scenario: ` / `Scenario Outline: ` / `Step: ` prefix that the
     * cucumber-javascript IntelliJ runner prepends to every test-tree node name.
     * Idempotent: once stripped, the presentableName no longer matches a prefix
     * so re-invocation (events + lazy-render paints) is a no-op.
     *
     * These labels are fixed English strings injected by the runner — NOT taken
     * from the Gherkin source — so a literal prefix match is safe across the
     * ~70 Gherkin dialects (the keyword in the actual .feature file is never
     * what cucumber-js puts here).
     */
    /** Classification derived from the runner's node-name prefix. */
    private enum class RunnerNodeKind { STEP, SCENARIO, NONE }

    private fun stripRunnerPrefix(suite: SMTestProxy): RunnerNodeKind {
        val current = suite.presentableName ?: suite.name
        val prefix = RUNNER_PREFIXES.firstOrNull { current.startsWith(it) }
            ?: return RunnerNodeKind.NONE   // cucumber-jvm (no prefix) or already stripped
        val stripped = current.removePrefix(prefix)
        if (stripped.isNotBlank() && stripped != current) {
            suite.setPresentableName(stripped)
        }
        return if (prefix == "Step: ") RunnerNodeKind.STEP else RunnerNodeKind.SCENARIO
    }

    /** Extract `FooBar.feature` from `file:///…/FooBar.feature:1` or `file:///…/FooBar.feature`. */
    private fun featureFileNameOf(locationUrl: String): String? {
        val withoutScheme = locationUrl
            .removePrefix("file:///")
            .removePrefix("file://")
        // Strip a trailing `:NN` line-number suffix if present.
        val withoutLine = if (withoutScheme.matches(Regex(".*:\\d+$"))) withoutScheme.substringBeforeLast(':') else withoutScheme
        val name = withoutLine.substringAfterLast('/').substringAfterLast('\\')
        return name.takeIf { it.endsWith(".feature", ignoreCase = true) }
    }

    companion object {
        private val LOG = Logger.getInstance(TzCucumberSuiteNameDecorator::class.java)
        private const val MARKER = "  /  "
        // Fixed English prefixes the cucumber-javascript runner prepends to its
        // SMTRunner node names. Order matters: the longer "Scenario Outline: "
        // must be tested before "Scenario: ".
        private val RUNNER_PREFIXES = listOf(
            "Scenario Outline: ",
            "Scenario Template: ",
            "Scenario: ",
            "Step: ",
        )
    }
}
