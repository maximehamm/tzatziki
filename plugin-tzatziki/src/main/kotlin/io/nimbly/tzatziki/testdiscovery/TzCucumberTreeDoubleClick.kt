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
import com.intellij.util.ui.UIUtil
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JTree
import javax.swing.SwingUtilities

/**
 * Makes double-clicking on a Cucumber Feature / Scenario node in the Run / Debug test
 * tree open the .feature file at the corresponding line, instead of just toggling the
 * node's expansion state (the platform default for composite nodes).
 *
 * Implementation: every time a tool window changes state we scan its component tree for
 * [SMTRunnerTestTreeView] instances and install a custom [MouseListener]. To make sure
 * our listener runs *before* the platform's `EditSourceOnDoubleClickHandler` (which is
 * the one that decides expand vs navigate), we re-order: detach all current listeners,
 * attach ours, re-attach the originals. Idempotent via a client property so repeated
 * stateChanged callbacks don't pile up listeners.
 */
class TzCucumberTreeDoubleClick : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(tm: ToolWindowManager) {
                    ApplicationManager.getApplication().invokeLater { scan(tm) }
                }
            }
        )
        // Initial pass for already-open Run/Debug tabs at startup.
        ApplicationManager.getApplication().invokeLater {
            scan(ToolWindowManager.getInstance(project))
        }
    }

    private fun scan(tm: ToolWindowManager) {
        for (id in TEST_TOOL_WINDOW_IDS) {
            val tw = tm.getToolWindow(id) ?: continue
            UIUtil.uiTraverser(tw.component)
                .filterIsInstance(JTree::class.java)
                .filterIsInstance(SMTRunnerTestTreeView::class.java)
                .forEach(::install)
        }
    }

    private fun install(tree: SMTRunnerTestTreeView) {
        if (tree.getClientProperty(INSTALLED_KEY) == true) return
        tree.putClientProperty(INSTALLED_KEY, true)

        // Detach all existing MouseListeners, attach ours first, re-attach the rest.
        // This makes our listener fire BEFORE the platform's double-click handler so we
        // can consume the event and prevent the default expand/collapse toggle.
        val existing: List<MouseListener> = tree.mouseListeners.toList()
        existing.forEach(tree::removeMouseListener)
        tree.addMouseListener(CucumberTreeDoubleClickAdapter(tree))
        existing.forEach(tree::addMouseListener)

        LOG.info("C+ TestTreeView double-click handler installed (${existing.size} listeners reordered)")
    }

    private class CucumberTreeDoubleClickAdapter(private val tree: SMTRunnerTestTreeView) : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            if (!TOGGLE_CUCUMBER_PL || e.clickCount != 2 || !SwingUtilities.isLeftMouseButton(e)) return
            val path = tree.getPathForLocation(e.x, e.y) ?: return
            val proxy = tree.getSelectedTest(path) as? SMTestProxy ?: return
            val url = proxy.locationUrl ?: return
            if (!url.contains(".feature", ignoreCase = true)) return
            // Step-level proxies already navigate by default — we only need to intervene
            // on composite (Feature / Scenario / Outline) nodes where the platform would
            // otherwise toggle expansion.
            if (proxy.children.isEmpty()) return
            try {
                proxy.navigate(true)
                e.consume()
            } catch (t: Throwable) {
                LOG.warn("C+ tree dbl-click navigate failed: ${t.message}")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(TzCucumberTreeDoubleClick::class.java)
        private const val INSTALLED_KEY = "tzatziki.cucumber.dblclick.installed"
        private val TEST_TOOL_WINDOW_IDS = listOf("Run", "Debug", "Services")
    }
}
