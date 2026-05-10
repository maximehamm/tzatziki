package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XSourcePositionImpl
import io.nimbly.tzatziki.breakpoints.TzCucumberCodeBreakpointType
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class TzAddCucumberBreakpointAction : DumbAwareAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val position = getLineBreakpointPosition(e) ?: return
        val type = XDebuggerUtil.getInstance()
            .findBreakpointType(TzCucumberCodeBreakpointType::class.java) ?: return
        val manager = XDebuggerManager.getInstance(project).breakpointManager

        val file = position.file
        val line = position.line

        // Toggle: if a Cucumber+ code breakpoint already exists at this file/line → remove it,
        // else create one (idempotent and self-contained, no fake condition needed).
        val existing = manager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .firstOrNull { bp ->
                bp.type === type
                    && bp.fileUrl == file.url
                    && bp.line == line
            }

        WriteAction.run<Throwable> {
            if (existing != null) {
                manager.removeBreakpoint(existing)
            } else {
                // Properties must be non-null — JavaBreakpointsUsageCollector NPEs otherwise.
                manager.addLineBreakpoint(type, file.url, line, JavaLineBreakpointProperties())
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = getLineBreakpointPosition(e) != null
    }


    private fun getLineBreakpointPosition(e: AnActionEvent): XSourcePosition? {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (file?.fileType == GherkinFileType.INSTANCE)
            return null

        if (project != null && editor != null && file != null) {
            val gutter = editor.gutter
            if (gutter is EditorGutterComponentEx) {
                val lineNumber = gutter.getClientProperty("active.line.number")
                if (lineNumber is Int) {
                    val pos = LogicalPosition(lineNumber, 0)
                    return XSourcePositionImpl.createByOffset(file, editor.logicalPositionToOffset(pos))
                }
            }
        }
        return null
    }
}
