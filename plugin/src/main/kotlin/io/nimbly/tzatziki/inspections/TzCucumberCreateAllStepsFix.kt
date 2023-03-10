package io.nimbly.tzatziki.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateAllStepsFix

class TzCucumberCreateAllStepsFix : CucumberCreateAllStepsFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        super.applyFix(project, descriptor)
    }
}