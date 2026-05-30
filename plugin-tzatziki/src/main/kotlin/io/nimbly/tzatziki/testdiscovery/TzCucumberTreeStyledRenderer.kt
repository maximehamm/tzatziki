/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.tzatziki.testdiscovery

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

/**
 * Wraps the cell renderer of every Cucumber test tree so the outermost suite nodes
 * render as multi-fragment styled text instead of a flat concatenated string.
 *
 *   `France.feature  /  Cocktail Ordering [toto]`
 *
 * becomes (visually):
 *
 *   `France.feature ` (grey)  `/`  `Cocktail Ordering` (bold)  ` [toto]` (grey italic)
 *
 * Implementation: install a delegating [TreeCellRenderer] that calls the platform's
 * original renderer first (preserving the status icon, indent, hover/selection
 * background) then mutates the returned [ColoredTreeCellRenderer] by clearing its text
 * fragments and re-appending them with the desired [SimpleTextAttributes].
 *
 * The decoration is read from `suite.userData[CUCUMBER_DECORATION_KEY]` — set upstream
 * by [TzCucumberSuiteNameDecorator]. Suites without that key fall through to the
 * original rendering.
 */
class TzCucumberTreeStyledRenderer : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowsRegistered(ids: MutableList<String>, manager: ToolWindowManager) {
                    ids.filter { it in TEST_TOOL_WINDOW_IDS }.forEach { id ->
                        manager.getToolWindow(id)?.let { tw ->
                            ApplicationManager.getApplication().invokeLater { scanComponent(tw.component) }
                        }
                    }
                }
                override fun stateChanged(tm: ToolWindowManager) {
                    // stateChanged is noisy but harmless here: install() is idempotent via
                    // RENDERER_INSTALLED_KEY, so re-scans during the user's normal interaction
                    // don't restack renderers. We rely on it to catch newly-opened test
                    // tabs that didn't exist at toolWindowsRegistered time.
                    ApplicationManager.getApplication().invokeLater {
                        for (id in TEST_TOOL_WINDOW_IDS) {
                            val tw = tm.getToolWindow(id) ?: continue
                            scanComponent(tw.component)
                        }
                    }
                }
            }
        )
        // Initial pass — covers test tabs persisted from a previous session.
        ApplicationManager.getApplication().invokeLater {
            val tm = ToolWindowManager.getInstance(project)
            for (id in TEST_TOOL_WINDOW_IDS) {
                val tw = tm.getToolWindow(id) ?: continue
                scanComponent(tw.component)
            }
        }
        LOG.info("C+ TzCucumberTreeStyledRenderer subscribed for project ${project.name}")
    }

    private fun scanComponent(root: java.awt.Component) {
        UIUtil.uiTraverser(root)
            .filterIsInstance(JTree::class.java)
            .filterIsInstance(SMTRunnerTestTreeView::class.java)
            .forEach(::install)
    }

    private fun install(tree: SMTRunnerTestTreeView) {
        if (tree.getClientProperty(RENDERER_INSTALLED_KEY) == true) return
        tree.putClientProperty(RENDERER_INSTALLED_KEY, true)
        val original = tree.cellRenderer ?: return
        tree.cellRenderer = StyledDelegateRenderer(original)
        LOG.info("C+ styled cell renderer installed on ${tree.javaClass.simpleName}")
    }

    private class StyledDelegateRenderer(private val delegate: TreeCellRenderer) : TreeCellRenderer {
        private val lazyDecorator = TzCucumberSuiteNameDecorator()

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            val result = delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            if (!TOGGLE_CUCUMBER_PL) return result
            if (result !is ColoredTreeCellRenderer) return result
            val proxy = SMTRunnerTestTreeView.getTestProxyFor(value) ?: return result

            // Safety net: some Cucumber runners (Windows + WSL setups reported by users)
            // don't publish SMTRunnerEvents through the project bus, so our normal listener
            // path in [TzCucumberSuiteNameDecorator] never fires. Trigger decoration here
            // lazily — first paint of the cell computes + caches, subsequent paints reuse.
            if (proxy.getUserData(CUCUMBER_DECORATION_KEY) == null
                && proxy.getUserData(CUCUMBER_WRAPPER_KEY) != true
                && proxy.getUserData(CUCUMBER_TAGS_KEY) == null
                && proxy.getUserData(CUCUMBER_EXAMPLE_KEY) == null
            ) {
                projectOf(tree)?.let { lazyDecorator.decorate(proxy, it, "lazy-render") }
            }

            // "Cucumber+" wrapper node: default style (no bold) — matches the file-name
            // styling on the suite below for visual consistency.
            if (proxy.getUserData(CUCUMBER_WRAPPER_KEY) == true) {
                val savedIcon = result.icon
                result.clear()
                result.icon = savedIcon
                result.append("Cucumber+", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                return result
            }

            // Outermost feature suite: file name in default style + separator + primary
            // (feature label) in grey + optional secondaries (BN/Ability) in grey italic
            // between brackets.
            val deco = proxy.getUserData(CUCUMBER_DECORATION_KEY)
            if (deco != null) {
                val savedIcon = result.icon
                result.clear()
                result.icon = savedIcon
                result.append(deco.fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (deco.primary != null) {
                    result.append("  /  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    result.append(deco.primary, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                if (deco.secondaries.isNotEmpty()) {
                    result.append(" [", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    deco.secondaries.forEachIndexed { i, s ->
                        if (i > 0) result.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        result.append(s, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                    }
                    result.append("]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                // Feature-level tags (e.g. `@global` above the `Feature:` keyword).
                val featureTags = proxy.getUserData(CUCUMBER_TAGS_KEY)
                if (featureTags != null && featureTags.isNotBlank()) {
                    result.append(featureTags, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                return result
            }

            // Scenario-level suite (or any non-outermost test holder with tags): keep the
            // platform's rendering intact and just append the tags in grey at the end.
            val tags = proxy.getUserData(CUCUMBER_TAGS_KEY)
            if (tags != null && tags.isNotBlank()) {
                result.append(tags, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            // Scenario Outline example row: append the example data with the header
            // names in grey italic and the cell values in plain grey, so visually
            // `Age: 22, Score: 75, …` reads as `<i>Age</i>: 22, <i>Score</i>: 75, …`.
            val example = proxy.getUserData(CUCUMBER_EXAMPLE_KEY)
            if (!example.isNullOrEmpty()) {
                result.append("  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                example.forEachIndexed { i, (header, value) ->
                    if (i > 0) result.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    result.append(header, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                    result.append(": ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    result.append(value, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                return result
            }

            // Step node inside a Cucumber+ tree: render the parameter parts (quoted
            // strings, numbers, `<placeholder>`) in grey italic — same family as the
            // suffix decorations above ([@tag] / "Age: 22, …"). We only restyle when
            // the platform's base text is plain (i.e. step PASSED): for failed /
            // skipped steps we leave the platform's red / grey alone, which is the
            // most important visual cue.
            val parentProxy = proxy.parent
            val insideCucumberTree = parentProxy != null && (
                parentProxy.getUserData(CUCUMBER_DECORATION_KEY) != null
                    || parentProxy.getUserData(CUCUMBER_TAGS_KEY) != null
                    || parentProxy.getUserData(CUCUMBER_EXAMPLE_KEY) != null
                )
            if (insideCucumberTree && proxy.isPassed) {
                styleStepParameters(result)
            }
            return result
        }

        /**
         * Re-renders the cell text in [comp] with Cucumber step parameters
         * (`"quoted"` strings, numbers, `<placeholders>`) in grey italic and the
         * surrounding step text in default attributes. No-op when no parameter
         * pattern is found.
         */
        private fun styleStepParameters(comp: ColoredTreeCellRenderer) {
            val full = comp.getCharSequence(false).toString()
            if (full.isBlank()) return
            val matches = STEP_PARAM_REGEX.findAll(full).toList()
            if (matches.isEmpty()) return
            val savedIcon = comp.icon
            comp.clear()
            comp.icon = savedIcon
            var cursor = 0
            for (m in matches) {
                if (m.range.first > cursor) {
                    comp.append(full.substring(cursor, m.range.first), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                comp.append(full.substring(m.range.first, m.range.last + 1), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                cursor = m.range.last + 1
            }
            if (cursor < full.length) comp.append(full.substring(cursor), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(TzCucumberTreeStyledRenderer::class.java)
        private const val RENDERER_INSTALLED_KEY = "tzatziki.cucumber.styled.renderer.installed"
        private val TEST_TOOL_WINDOW_IDS = listOf("Run", "Debug")
        /** Matches the common Cucumber step parameters: `"quoted"` strings, integers
         *  and decimals, and `<placeholder>` references for un-substituted Scenario
         *  Outline steps. */
        private val STEP_PARAM_REGEX = Regex("""("[^"]*")|(\b\d+(?:[.,]\d+)?\b)|(<[^<>]+>)""")

        /** Best-effort lookup of the [com.intellij.openapi.project.Project] hosting a tree. */
        private fun projectOf(tree: JTree): com.intellij.openapi.project.Project? {
            // The tree itself or its ancestors should provide the project via the
            // standard DataManager mechanism (set up by IntelliJ's content/tool window).
            return runCatching {
                val ctx = com.intellij.ide.DataManager.getInstance().getDataContext(tree)
                com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.getData(ctx)
            }.getOrNull()
                ?: com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
        }
    }
}
