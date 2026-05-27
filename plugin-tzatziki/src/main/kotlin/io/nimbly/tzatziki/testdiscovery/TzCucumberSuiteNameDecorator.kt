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
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

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
        project.messageBus.connect().subscribe(
            SMTRunnerEventsListener.TEST_STATUS,
            object : SMTRunnerEventsAdapter() {
                override fun onSuiteStarted(suite: SMTestProxy) = decorate(suite, project, "started")
                override fun onSuiteTreeNodeAdded(suite: SMTestProxy) = decorate(suite, project, "treeAdded")
                override fun onSuiteTreeStarted(suite: SMTestProxy) = decorate(suite, project, "treeStarted")
            }
        )
        LOG.info("C+ TzCucumberSuiteNameDecorator subscribed for project ${project.name}")
    }

    private fun decorate(suite: SMTestProxy, project: Project, phase: String) {
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
            if (suite.getUserData(CUCUMBER_TAGS_KEY) == null) {
                val tags = readScenarioTags(project, locationUrl)
                if (tags.isNotEmpty()) {
                    suite.putUserData(CUCUMBER_TAGS_KEY, tags.joinToString(" ", prefix = " "))
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
        val pairs = readFeatureKeywordPairs(project, locationUrl)
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
            val featureTags = readScenarioTags(project, locationUrl)
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
    private fun readFeatureKeywordPairs(project: Project, locationUrl: String): List<Pair<String, String>> {
        // Strip the trailing `:lineNumber` (teamcity locationUrl convention).
        val cleanUrl = if (locationUrl.matches(Regex(".*:\\d+$"))) locationUrl.substringBeforeLast(':') else locationUrl
        // Resolve via VFS by URL first; fall back to file-system path if that fails
        // (encountered on Windows where `file:///C:/...` URL encoding is finicky and
        // findFileByUrl occasionally returns null — issue reported by users).
        val vfile = VirtualFileManager.getInstance().findFileByUrl(cleanUrl)
            ?: run {
                val plainPath = cleanUrl
                    .removePrefix("file:///")
                    .removePrefix("file://")
                    .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(plainPath)
            }
            ?: run {
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
    private fun readScenarioTags(project: Project, locationUrl: String): List<String> {
        val match = Regex("(.*\\.feature):(\\d+)$").matchEntire(locationUrl) ?: return emptyList()
        val cleanUrl = match.groupValues[1]
        val lineNumber = match.groupValues[2].toIntOrNull() ?: return emptyList()
        val vfile = VirtualFileManager.getInstance().findFileByUrl(cleanUrl)
            ?: run {
                val plainPath = cleanUrl
                    .removePrefix("file:///")
                    .removePrefix("file://")
                    .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(plainPath)
            }
            ?: return emptyList()
        return runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@runReadAction emptyList()
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vfile)
                ?: return@runReadAction emptyList()
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
            val holder = element?.let { PsiTreeUtil.getParentOfType(it, GherkinStepsHolder::class.java, false) }
                ?: PsiTreeUtil.findChildOfType(psiFile, GherkinFeature::class.java)
                ?: return@runReadAction emptyList()

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
    }
}
