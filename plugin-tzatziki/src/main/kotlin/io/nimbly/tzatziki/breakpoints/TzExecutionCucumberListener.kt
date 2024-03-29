package io.nimbly.tzatziki.breakpoints

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.nimbly.tzatziki.TOGGLE_CUCUMBER_PL
import io.nimbly.tzatziki.util.toPath

class TzExecutionCucumberListener : StartupActivity {

    private val LOG = logger<TzExecutionCucumberListener>()
    private val REGEX = Regex(" locationHint = '(?:file://)?([^:]+):(\\d+)'(?: name = 'Example #(\\d+)')?")

    companion object {

        private val CUCUMBER_EXECUTION_POINT: Key<TzExecutionTracker> = Key.create("CUCUMBER_EXECUTION_POSITION")

        fun Project.cucumberExecutionTracker(): TzExecutionTracker {
            var p = this.getUserData(CUCUMBER_EXECUTION_POINT)
            if (p == null) {
                p = TzExecutionTracker()
                this.putUserData(CUCUMBER_EXECUTION_POINT, p)
            }
            return p
        }
    }

    data class TzExecutionTracker(
        var featurePath: String? = null,
        var lineNumber: Int? = null,
        var exampleLine: Int? = null,

        val highlighters: MutableList<RangeHighlighter> = mutableListOf(),
        var highlightersModel: MarkupModel? = null
    ) {

        fun clear() {
            featurePath = null
            lineNumber = null
            exampleLine = null
        }

        fun removeHighlighters() {

            val model = this.highlightersModel ?: return
            val copy = this.highlighters.toList() ?: return

            this.highlighters.clear()

            ApplicationManager.getApplication().invokeLater {
                copy.forEach {
                    model.removeHighlighter(it)
                }
            }
        }

        fun findFile(): VirtualFile? {
            var p = featurePath ?: return null
            if (p.matches("^/[A-Z]:.*".toRegex()))
                p = p.substring(1)
            val toPath = p.toPath()
            return LocalFileSystem.getInstance().findFileByNioFile(toPath)
        }
    }

    override fun runActivity(project: Project) {

        project.messageBus
            .connect()
            .subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {

                private var listener: ProcessListener? = null

                override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {

                    if (!TOGGLE_CUCUMBER_PL)
                        return
                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - processStarting")

                    val tracker = project.cucumberExecutionTracker()
                    tracker.clear()

                    val listener = object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {

                            val values = REGEX.find(event.text)?.groupValues
                                ?:return

                            println(event.text)
                            LOG.debug("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - " + event.text)

                            val path = values[1].trim()
                            val line = values[2].toInt()
                            val isExample = values[3].isNotEmpty()

                            if (!isExample) {

                                LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - filePath = $path line $line")
                                val p = project.cucumberExecutionTracker()
                                p.featurePath = path
                                p.lineNumber = line - 1
                            }
                            else {

                                LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - exampleLine = $line")

                                val p = project.cucumberExecutionTracker()
                                p.exampleLine = line
                            }
                        }
                    }
                    handler.addProcessListener(listener)
                    this.listener = listener
                }

                override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {

                    if (!TOGGLE_CUCUMBER_PL)
                        return
                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - processTerminated")

                    project.cucumberExecutionTracker().removeHighlighters()
                }

            })
    }
}