package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class TzBreakpointProjectListener : StartupActivity {

    override fun runActivity(project: Project) {

        project.messageBus
            .connect()
            .subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
                override fun breakpointChanged(breakpoint: XBreakpoint<*>) =
                    refresh(breakpoint, EAction.CHANGED)

                override fun breakpointAdded(breakpoint: XBreakpoint<*>) =
                    refresh(breakpoint, EAction.ADDED)

                override fun breakpointRemoved(breakpoint: XBreakpoint<*>) =
                    refresh(breakpoint, EAction.REMOVED)

                override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) = Unit
            })
    }

    private fun refresh(breakpoint: XBreakpoint<*>, action: EAction) {

        if (breakpoint.sourcePosition?.file?.fileType == GherkinFileType.INSTANCE) {
            refreshGherkin(breakpoint, action)
        } else {
            refreshCode(breakpoint, action)
        }
    }

    private fun refreshGherkin(breakpoint: XBreakpoint<*>, action: EAction) {

        val vfile = breakpoint.sourcePosition?.file ?: return
        val line = breakpoint.sourcePosition?.line ?: return

        val project = ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .firstOrNull() { vfile.getFile(it) != null }
            ?: return

        val step = findStep(vfile, project, line)
            ?: return

        val stepDefinitions = step.findCucumberStepDefinitions()
        if (stepDefinitions.isEmpty())
            return

        stepDefinitions.first().element

        val breakPointElement = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findBestPositionToAddBreakpoint(stepDefinitions)
        } ?: return

        val currentBreakpoints = Tzatziki().extensionList.firstNotNullOfOrNull {

            val offset = breakPointElement.first.containingFile.getDocument()?.getLineStartOffset(breakPointElement.second)
            it.findStepsAndBreakpoints(
                breakPointElement.first.containingFile.virtualFile,
                offset
            )
        }

        if (action == EAction.ADDED) {

            if (currentBreakpoints?.second?.isEmpty() == true) {
                XDebuggerUtil.getInstance().toggleLineBreakpoint(
                    project,
                    breakPointElement.first.containingFile.virtualFile,
                    breakPointElement.second)
            }
        } else if (action == EAction.REMOVED) {

            currentBreakpoints?.second?.forEach { b ->
                XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
            }
        } else if (action == EAction.CHANGED) {

            val enabled = breakpoint.isEnabled
            val condition = breakpoint.conditionExpression

            currentBreakpoints?.second?.forEach { b ->
                b.isEnabled = enabled
                b.conditionExpression = condition
            }
        }
    }

    private fun refreshCode(breakpoint: XBreakpoint<*>, action: EAction) {

        val pair = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findStepsAndBreakpoints(
                breakpoint.sourcePosition?.file,
                breakpoint.sourcePosition?.offset)
        } ?: return

        val steps = pair.first
        val remainingBreakpoints = pair.second

        steps.forEach { step ->

            val documentLine = step.getDocumentLine()
                ?: return@forEach

            if (action == EAction.ADDED && remainingBreakpoints.size == 1) {

                val oldBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
                    .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                    .filter { it.sourcePosition?.line == step.getDocumentLine() }

                if (oldBreakpoints.isEmpty()) {
                    toggleBreakpoint(step, documentLine)
                    step.updatePresentation(remainingBreakpoints)
                }
            }
            else if (action == EAction.REMOVED && remainingBreakpoints.size == 0) {
                deleteBreakpoints(step)
            }

            step.updatePresentation(remainingBreakpoints)
        }
    }

    private fun toggleBreakpoint(step: GherkinStep, documentLine: Int) {
        XDebuggerUtil.getInstance().toggleLineBreakpoint(
            step.project,
            step.containingFile.virtualFile,
            documentLine
        )
    }

    private fun deleteBreakpoints(step: GherkinStep) {
        val oldBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
            .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
            .filter { it.sourcePosition?.line == step.getDocumentLine() }
        oldBreakpoints.forEach { b ->
            XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
        }
    }

}

enum class EAction { CHANGED, ADDED, REMOVED }
