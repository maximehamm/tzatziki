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
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            val result = delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            if (!TOGGLE_CUCUMBER_PL) return result
            if (result !is ColoredTreeCellRenderer) return result
            val proxy = SMTRunnerTestTreeView.getTestProxyFor(value) ?: return result

            // "Cucumber+" wrapper node: just bold.
            if (proxy.getUserData(CUCUMBER_WRAPPER_KEY) == true) {
                val savedIcon = result.icon
                result.clear()
                result.icon = savedIcon
                result.append("Cucumber+", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
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
            return result
        }
    }

    companion object {
        private val LOG = Logger.getInstance(TzCucumberTreeStyledRenderer::class.java)
        private const val RENDERER_INSTALLED_KEY = "tzatziki.cucumber.styled.renderer.installed"
        private val TEST_TOOL_WINDOW_IDS = listOf("Run", "Debug")
    }
}
