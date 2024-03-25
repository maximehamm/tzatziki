package io.nimbly.tzatziki.breakpoints

import com.intellij.icons.AllIcons.Debugger.Db_dep_field_breakpoint
import com.intellij.icons.AllIcons.Debugger.Db_field_breakpoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.cucumber.psi.*

const val CUCUMBER_FAKE_EXPRESSION = "\"Cucumber+\"!=null"

class TzBreakpointListener : StartupActivity {

    override fun runActivity(project: Project) {

        var modificationInProgress = false
        var addInProgress = false
        project.messageBus
            .connect()
            .subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {

                override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
                    if (modificationInProgress)
                        return
                    try {
                        modificationInProgress = true
                        refresh(breakpoint, EAction.CHANGED)
                    } finally {
                        modificationInProgress = false
                    }
                }

                override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
                    if (addInProgress)
                        return
                    try {
                        addInProgress = true
                        refresh(breakpoint, EAction.ADDED)
                    } finally {
                        addInProgress = false
                    }
                }

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

    private fun refreshGherkin(gherkinBreakpoint: XBreakpoint<*>, action: EAction) {

        val vfile = gherkinBreakpoint.sourcePosition?.file ?: return
        val line = gherkinBreakpoint.sourcePosition?.line ?: return
        val project = vfile.findProject() ?: return

        val step = findStep(vfile, project, line)
            ?: return

        val stepDefinitions = step.findCucumberStepDefinitions()
        if (stepDefinitions.isEmpty())
            return

        stepDefinitions.first().element

        val codeBreakPointElt = Tzatziki().extensionList.firstNotNullOfOrNull {
            it.findBestPositionToAddBreakpoint(stepDefinitions)
        } ?: return

        val allCodeBreakpoints = Tzatziki().extensionList.firstNotNullOfOrNull {
            val offset = codeBreakPointElt.first.containingFile.getDocument()?.getLineStartOffset(codeBreakPointElt.second)
            it.findStepsAndBreakpoints(
                codeBreakPointElt.first.containingFile.virtualFile,
                offset
            )
        }

        if (action == EAction.ADDED) {

            if (allCodeBreakpoints?.second?.isEmpty() == true) {
              (XDebuggerUtil.getInstance() as? XDebuggerUtilImpl)?.toggleAndReturnLineBreakpoint(
                    project,
                    codeBreakPointElt.first.containingFile.virtualFile,
                    codeBreakPointElt.second, false)
                    ?.then { it: XLineBreakpoint<out XBreakpointProperties<*>> ->
                        it.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
                        (it.properties as? JavaLineBreakpointProperties)?.let { it.lambdaOrdinal =  -1 } // TIPS : Fix for a Kotlin exception is some cases....
                    }
            }
            allCodeBreakpoints?.second?.forEach { b ->
                b.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
            }
        } else if (action == EAction.REMOVED) {

            allCodeBreakpoints?.second?.forEach { b ->
                XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
            }
        } else if (action == EAction.CHANGED) {

            allCodeBreakpoints?.second?.forEach { b ->
                b.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
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
        val codeBreakpoints = pair.second
        val createdFromCode = breakpoint.conditionExpression == null

        if (steps.isNotEmpty() && breakpoint.conditionExpression == null)
            breakpoint.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)

        steps.forEach { step ->

            val documentLine = step.getDocumentLine()
                ?: return@forEach

            if (action == EAction.ADDED && codeBreakpoints.size == 1) {

                if (createdFromCode) {

                    val oldStepBreakpoints = XDebuggerManager.getInstance(step.project).breakpointManager.allBreakpoints
                        .filter { it.sourcePosition?.file == step.containingFile.virtualFile }
                        .filter { it.sourcePosition?.line == step.getDocumentLine() }

                    if (oldStepBreakpoints.isEmpty()) {
                        toggleBreakpoint(step, documentLine)
                        step.updatePresentation(codeBreakpoints)
                    }
                }
            }
            else if (action == EAction.REMOVED && codeBreakpoints.size == 0) {
                deleteBreakpoints(step)
            }
            else {
                step.updatePresentation(codeBreakpoints)
            }
        }
    }

    private fun toggleBreakpoint(step: GherkinStep, documentLine: Int) {
        (XDebuggerUtil.getInstance() as? XDebuggerUtilImpl)?.toggleAndReturnLineBreakpoint(
            step.project,
            step.containingFile.virtualFile,
            documentLine, false)
            ?.then { it: XLineBreakpoint<out XBreakpointProperties<*>> ->
                it.conditionExpression = null
            }
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
