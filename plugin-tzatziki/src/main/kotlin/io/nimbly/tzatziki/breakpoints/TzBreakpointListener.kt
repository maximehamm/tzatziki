package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.*
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl

const val CUCUMBER_FAKE_EXPRESSION = "\"Cucumber+\"!=null"

class TzBreakpointListener : StartupActivity {

    override fun runActivity(project: Project) {

        var changeInProgress = false
        var addInProgress = false
        var removeInProgress = false

        project.messageBus
            .connect()
            .subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {

                override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
                    if (changeInProgress)
                        return
                    try {
                        changeInProgress = true
                        refresh(breakpoint, EAction.CHANGED)
                    } finally {
                        changeInProgress = false
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

                override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
                    if (removeInProgress)
                        return
                    try {
                        removeInProgress = true
                        refresh(breakpoint, EAction.REMOVED)
                    } finally {
                        removeInProgress = false
                    }
                }

                override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) = Unit

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

                    val file = vfile.getFile(project) ?: return
                    val doc = file.getDocument() ?: return
                    val lineRange = doc.getLineRange(line).shrink(1, 1)

                    val step = file.findElementsOfTypeInRange(lineRange, GherkinStep::class.java).firstOrNull()
                    if (step != null && line == step.getDocumentLine())
                        refreshGherkinStep(step, gherkinBreakpoint, action)

                    val row = file.findElementsOfTypeInRange(lineRange, GherkinTableRow::class.java).firstOrNull()
                    if (row != null && row !is GherkinTableHeaderRowImpl)
                        refreshGherkinRow(row, gherkinBreakpoint, action)

                }

                private fun refreshGherkinRow(row: GherkinTableRow, gherkinBreakpoint: XBreakpoint<*>, action: EAction) {

                    val examples = row.parentOfTypeIs<GherkinExamplesBlock>(true) ?: return
                    val scenario = examples.parentOfTypeIs<GherkinScenarioOutline>(true) ?: return

                    if (action == EAction.ADDED) {

                        // Check if at least one breakpoint is set
                        if (scenario.steps.find { it.findBreakpoint() != null } != null)
                            return

                        // Add step breakpoints
                        scenario.steps.forEach { step ->

                            val documentLine = step.getDocumentLine() ?: return@forEach
                            step.toggleGherkinBreakpoint(documentLine)

                            // Refresh step (i.e add code breakpoint if needed)
                            addInProgress = false // Let recurse !
                            refreshGherkinStep(step, null, EAction.ADDED)
                        }
                    }
                    else if (action == EAction.REMOVED) {

                        // Remove step's breakpoint if no more row breakpoint exists
                        val hasStillRowBreakpoint = scenario.allExamples().find { it.findBreakpoint() != null } != null
                        if (!hasStillRowBreakpoint) {
                            scenario.steps.forEach { it.deleteBreakpoints() }
                        }
                    }
                    else if (action == EAction.CHANGED) {

                        // Sync row breakpoint if all step's breakpoint has same state
                        val state = gherkinBreakpoint.isEnabled
                        if (gherkinBreakpoint.isEnabled ||
                            scenario.allExamples().filter { it != row }.find { it.findBreakpoint() != null && it.findBreakpoint()?.isEnabled != gherkinBreakpoint.isEnabled } == null) {

                            scenario.steps.forEach {
                                it.enableBreakpoints(state)
                            }
                        }
                    }
                }

                private fun refreshGherkinStep(step: GherkinStep, gherkinBreakpoint: XBreakpoint<*>?, action: EAction) {

                    val stepDefinitions = step.findCucumberStepDefinitions()
                    if (stepDefinitions.isEmpty())
                        return

                    val codeElement = Tzatziki().extensionList.firstNotNullOfOrNull {
                        it.findBestPositionToAddBreakpoint(stepDefinitions)
                    } ?: return

                    val allCodeBreakpoints = Tzatziki.findStepsAndBreakpoints(
                        codeElement.first.containingFile.virtualFile,
                        codeElement.first.containingFile.getDocument()?.getLineStartOffset(codeElement.second)
                    )

                    if (action == EAction.ADDED) {

                        // Add code breakpoints
                        if (allCodeBreakpoints?.second?.isEmpty() == true) {
                            toggleCodeBreakpoint(codeElement, project)
                        }
                        allCodeBreakpoints?.second?.forEach { b ->
                            b.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
                        }

                        // Add example breakpoints
                        val scenario = step.parentOfTypeIs<GherkinScenarioOutline>(true)
                        if (scenario != null) {
                            val examples = scenario.allExamples()
                            val hasBreakpoints = examples.find { it.findBreakpoint() != null } != null
                            if (!hasBreakpoints) {
                                examples.forEach {

                                    // Add step breakpoint
                                    val documentLine = it.getDocumentLine() ?: return@forEach
                                    it.toggleGherkinBreakpoint(documentLine)
                                }
                            }
                        }

                    }
                    else if (action == EAction.REMOVED) {

                        // Count the number of gherkin breakpoint link to this code breakpoint
                        val stepBreakpoints = allCodeBreakpoints?.first?.map { it.findBreakpoint() }?.filterNotNull()?.size

                        // If there is more gherkin breakpoint, then remove the related code breakpoint
                        if (stepBreakpoints == 0) {
                            allCodeBreakpoints.second.forEach { b ->
                                XDebuggerUtil.getInstance().removeBreakpoint(step.project, b)
                            }
                        }

                        // Remove row breakspoints
                        val scenarioStillHasBreakpoints = step.stepHolder.steps.find { it.findBreakpoint() != null } != null
                        if (step.stepHolder is GherkinScenarioOutline && !scenarioStillHasBreakpoints) {

                            val allExamples = (step.stepHolder as GherkinScenarioOutline).allExamples()
                            allExamples.forEach { it.deleteBreakpoints() }
                        }

                    }
                    else if (action == EAction.CHANGED && gherkinBreakpoint!=null) {

                        // Fix code breakpoint (if needed)
                        allCodeBreakpoints?.second?.forEach { b ->
                            b.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
                        }

                        // Sync row breakpoint if all step's breakpoint has same state
                        val state = gherkinBreakpoint.isEnabled
                        val scenario = step.parentOfTypeIs<GherkinScenarioOutline>(true)
                        if (scenario != null) {

                            if (gherkinBreakpoint.isEnabled ||
                                scenario.steps.filter { it != step }.find { it.findBreakpoint() != null && it.findBreakpoint()?.isEnabled != gherkinBreakpoint.isEnabled } == null) {

                                scenario.allExamples().forEach {
                                    it.enableBreakpoints(state)
                                }
                            }
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
                                    step.toggleGherkinBreakpoint(documentLine)
                                    step.updatePresentation(codeBreakpoints)
                                }
                            }
                        }
                        else if (action == EAction.REMOVED && codeBreakpoints.size == 0) {
                            step.deleteBreakpoints()
                        }
                        else {
                            step.updatePresentation(codeBreakpoints)
                        }
                    }
                }
            })
    }

    private fun GherkinPsiElement.enableBreakpoints(enabled: Boolean) {
        val oldBreakpoints = XDebuggerManager.getInstance(project).breakpointManager.allBreakpoints
            .filter { it.sourcePosition?.file == containingFile.virtualFile }
            .filter { it.sourcePosition?.line == getDocumentLine() }
        oldBreakpoints.forEach { b ->
            b.isEnabled = enabled
        }
    }

}

enum class EAction { CHANGED, ADDED, REMOVED }

