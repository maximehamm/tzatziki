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
import io.nimbly.tzatziki.util.toPath

class TzExecutionCucumberListener : StartupActivity {

    private val LOG = logger<TzExecutionCucumberListener>()

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
        var exampleNumber: Int = 0,

        val highlighters: MutableList<RangeHighlighter> = mutableListOf(),
        var highlightersModel: MarkupModel? = null
    ) {

        fun clear() {
            featurePath = null
            lineNumber = null
            exampleNumber = 0
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
            val toPath = featurePath?.toPath() ?: return null
            return LocalFileSystem.getInstance().findFileByNioFile(toPath)
        }
    }

    override fun runActivity(project: Project) {

        project.messageBus
            .connect()
            .subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {

                private var listener: ProcessListener? = null

                override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {

                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - processStarting")

                    project.cucumberExecutionTracker().clear()
                    val listener = object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {

                            LOG.debug("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - " + event.text)
                            val regex = Regex(" locationHint = '(?:file://)?([^']+)")
                            val filePathAndPosition = regex.find(event.text)?.groupValues?.get(1)?.trim()
                            if (filePathAndPosition != null) {
                                if (filePathAndPosition.lastIndexOf(':') < 1) return
                                val filePath = filePathAndPosition.substringBeforeLast(':')
                                val fileLine = filePathAndPosition.substringAfterLast(':').toIntOrNull() ?: return

                                LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - filePath = $filePath line $fileLine")
                                val p = project.cucumberExecutionTracker()
                                p.featurePath = filePath
                                p.lineNumber = fileLine - 1
                            }
                            else {

                                val regex2 = Regex(" name = 'Example #(\\d+)'")
                                val exampleNumber = regex2.find(event.text)?.groupValues?.get(1)?.toInt()
                                if (exampleNumber != null) {

                                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - onTextAvailable - exampleNumber = $exampleNumber")

                                    val p = project.cucumberExecutionTracker()
                                    p.exampleNumber = exampleNumber
                                }
                            }
                        }
                    }
                    handler.addProcessListener(listener)
                    this.listener = listener
                }

                override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {

                    LOG.info("C+ ExecutionManager.EXECUTION_TOPIC - processTerminated")

                    project.cucumberExecutionTracker().removeHighlighters()
                }

            })
    }
}