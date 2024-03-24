package io.nimbly.tzatziki.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange

@Deprecated("TO REMOVE")
class TzSourcePositionHighlighter : SourcePositionHighlighter(), DumbAware {

    override fun getHighlightRange(sourcePosition: SourcePosition?): TextRange {
        return TextRange(1, 200)
    }
}