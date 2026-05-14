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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

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
                LOG.info("C+ decorate[$phase] wrapper 'Cucumber' -> 'Cucumber+'")
            }
            return
        }

        // 2) Outermost feature-level suite — it carries a `.feature` locationUrl AND its
        //    parent does NOT (so we skip step-level suites that also point to .feature).
        val locationUrl = suite.locationUrl ?: return
        val fileName = featureFileNameOf(locationUrl) ?: return
        val parentUrl = suite.parent?.locationUrl
        if (parentUrl != null && featureFileNameOf(parentUrl) != null) return

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
        val decorated = when {
            pairs.isEmpty() -> "$fileName$MARKER$current"
            pairs.size == 1 -> {
                val (kw, name) = pairs.first()
                if (kw.equals("Business Need", true) || kw.equals("Ability", true))
                    "$fileName [$name]"
                else
                    "$fileName$MARKER$name"
            }
            else -> {
                val primary = pairs.last().second
                val secondary = pairs.dropLast(1).joinToString(", ") { it.second }
                "$fileName$MARKER$primary [$secondary]"
            }
        }
        suite.setPresentableName(decorated)
        LOG.info("C+ decorate[$phase] '${suite.name}' loc='$locationUrl' -> '$decorated'")
    }

    /**
     * Returns the (keyword, name) pairs following each FEATURE_KEYWORD inside the feature,
     * in source order. Walks the whole subtree because cucumber-plugin parses a second
     * `Feature:` (or `Ability:`) sitting under a `Business Need:` as a FEATURE_KEYWORD
     * *inside* the enclosing GherkinFeatureHeader — invisible to a one-level walk.
     */
    private fun readFeatureKeywordPairs(project: Project, locationUrl: String): List<Pair<String, String>> {
        // Strip the trailing `:lineNumber` (teamcity locationUrl convention) before
        // resolving — `VirtualFileManager.findFileByUrl` expects a plain `file://…` URL.
        // Plus it transparently handles Unix vs Windows paths, escaping, etc.
        val cleanUrl = if (locationUrl.matches(Regex(".*:\\d+$"))) locationUrl.substringBeforeLast(':') else locationUrl
        val vfile = VirtualFileManager.getInstance().findFileByUrl(cleanUrl) ?: return emptyList()
        return runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return@runReadAction emptyList()
            val feature = PsiTreeUtil.findChildOfType(psiFile, GherkinFeature::class.java)
                ?: return@runReadAction emptyList()
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
            walk(feature.node)
            pairs
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
