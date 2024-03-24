package io.nimbly.tzatziki.breakpoints

import com.intellij.xdebugger.impl.XDebuggerSupport
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler

class TzDebuggerSupport : XDebuggerSupport()  {

    override fun getEvaluateHandler(): DebuggerActionHandler {
        return super.getEvaluateHandler()
    }
}