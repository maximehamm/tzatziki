package io.nimbly.tzatziki.breakpoints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import io.nimbly.tzatziki.util.findStep
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

class TzBreakpointType() :
    XLineBreakpointType<TzXBreakpointProperties>("tzatziki.breakpoint.type", "Cucumber+ Breakpoint") {

    override fun canPutAt(vfile: VirtualFile, line: Int, project: Project): Boolean {

        val step = findStep(vfile, project, line)
            ?: return false

        val reference = step.references
            .filterIsInstance<CucumberStepReference>()
            .firstOrNull()
            ?: return false

        reference.resolveToDefinition()
            ?: return false

        return true
    }

    override fun createBreakpointProperties(vfile: VirtualFile, line: Int): TzXBreakpointProperties? {
        return null
    }

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<TzXBreakpointProperties>,
        project: Project
    ): XDebuggerEditorsProvider {
        return TzBreakpointProvider()
    }
}

class TzXBreakpointProperties : XBreakpointProperties<Any>() {
    override fun getState(): Any? {
        TODO("Not yet implemented")
    }
    override fun loadState(state: Any) {
        TODO("Not yet implemented")
    }

}

class TzBreakpointProvider : XDebuggerEditorsProviderBase() {

    override fun getFileType(): FileType {
        return GherkinFileType.INSTANCE
    }

    override fun createExpressionCodeFragment(
        project: Project,
        text: String,
        context: PsiElement?,
        isPhysical: Boolean ): PsiFile? {

        return context?.containingFile
    }

}