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
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.UIUtil
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.SwingUtilities

/**
 * Makes double-clicking on a Cucumber Feature / Scenario node in the Run / Debug test
 * tree open the .feature file at the corresponding line, instead of just toggling the
 * node's expansion state.
 *
 * Performance note (issue #122): the previous implementation hooked into
 * `ToolWindowManagerListener.stateChanged` and re-traversed Run/Debug/Services tool
 * window component trees on every fire — which happens **continuously** as the user
 * interacts with the IDE (focus / tab / content changes). That walk over the deeply
 * nested Services tool window in particular pinned the CPU at 80-90 %.
 *
 * Current design: attach a [ContentManagerListener] to Run/Debug ONLY when their tool
 * windows become available, and traverse exclusively the newly added [Content] component
 * tree (typically one [SMTRunnerTestTreeView]). No global polling, no Services
 * traversal, and the listener fires at most once per test-run content creation.
 */
class TzCucumberTreeDoubleClick : ProjectActivity {

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val tm = ToolWindowManager.getInstance(project)

            // Cover tool windows that already exist (e.g. IDE reopened with prior content).
            TEST_TOOL_WINDOW_IDS.forEach { id ->
                tm.getToolWindow(id)?.let { attachContentListener(project, it) }
            }

            // Cover tool windows that aren't registered yet (typical first-launch case —
            // "Run" is only created when the first run configuration is launched).
            project.messageBus.connect().subscribe(
                ToolWindowManagerListener.TOPIC,
                object : ToolWindowManagerListener {
                    override fun toolWindowsRegistered(ids: MutableList<String>, manager: ToolWindowManager) {
                        ids.filter { it in TEST_TOOL_WINDOW_IDS }.forEach { id ->
                            manager.getToolWindow(id)?.let { attachContentListener(project, it) }
                        }
                    }
                }
            )
        }
    }

    private fun attachContentListener(project: Project, tw: ToolWindow) {
        if (tw.component.getClientProperty(TW_LISTENER_KEY) == true) return
        tw.component.putClientProperty(TW_LISTENER_KEY, true)

        tw.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                ApplicationManager.getApplication().invokeLater { scanContent(event.content) }
            }
        })
        // Initial pass for content already present in the tool window.
        tw.contentManager.contents.forEach { scanContent(it) }
    }

    private fun scanContent(content: Content) {
        UIUtil.uiTraverser(content.component)
            .filterIsInstance(SMTRunnerTestTreeView::class.java)
            .forEach(::install)
    }

    private fun install(tree: SMTRunnerTestTreeView) {
        if (tree.getClientProperty(INSTALLED_KEY) == true) return
        tree.putClientProperty(INSTALLED_KEY, true)

        // Detach all existing MouseListeners, attach ours first, re-attach the rest so our
        // listener fires BEFORE the platform's EditSourceOnDoubleClickHandler.
        val existing: List<MouseListener> = tree.mouseListeners.toList()
        existing.forEach(tree::removeMouseListener)
        tree.addMouseListener(CucumberTreeDoubleClickAdapter(tree))
        existing.forEach(tree::addMouseListener)
    }

    private class CucumberTreeDoubleClickAdapter(private val tree: SMTRunnerTestTreeView) : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            if (!TOGGLE_CUCUMBER_PL || e.clickCount != 2 || !SwingUtilities.isLeftMouseButton(e)) return
            val path = tree.getPathForLocation(e.x, e.y) ?: return
            val proxy = tree.getSelectedTest(path) as? SMTestProxy ?: return
            val url = proxy.locationUrl ?: return
            if (!url.contains(".feature", ignoreCase = true)) return
            if (proxy.children.isEmpty()) return  // leaves already navigate by default
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
        private const val TW_LISTENER_KEY = "tzatziki.cucumber.dblclick.tw.listener"
        // Intentionally NOT including "Services" — its component tree is huge and never
        // hosts SMTRunnerTestTreeView; scanning it was the perf hog before issue #122.
        private val TEST_TOOL_WINDOW_IDS = listOf("Run", "Debug")
    }
}
