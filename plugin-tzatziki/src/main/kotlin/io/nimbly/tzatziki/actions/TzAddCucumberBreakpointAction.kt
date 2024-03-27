package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import io.nimbly.tzatziki.breakpoints.CUCUMBER_FAKE_EXPRESSION

class TzAddCucumberBreakpointAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val position = getLineBreakpointPosition(e) ?: return
        XBreakpointUtil.toggleLineBreakpoint( project, position, editor, false, true, true)
            .onSuccess { bp: XLineBreakpoint<*> ->
                bp.conditionExpression = XExpressionImpl.fromText(CUCUMBER_FAKE_EXPRESSION)
            }
    }

    override fun update(e: AnActionEvent) {
        getLineBreakpointPosition(e) != null
    }

//    override fun getActionUpdateThread(): ActionUpdateThread {
//        return ActionUpdateThread.BGT
//    }

    private fun getLineBreakpointPosition(e: AnActionEvent): XSourcePosition? {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
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