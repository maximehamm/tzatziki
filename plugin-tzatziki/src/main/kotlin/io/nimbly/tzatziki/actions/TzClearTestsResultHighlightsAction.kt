package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import io.nimbly.tzatziki.run.cucumberExecutionTracker
import io.nimbly.tzatziki.testdiscovery.ClearAnnotationsFix
import io.nimbly.tzatziki.testdiscovery.TzTestRegistry

class TzClearTestsResultHighlightsAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {

        val file = CommonDataKeys.PSI_FILE.getData(e.dataContext)
        if (file != null) {
            ClearAnnotationsFix.clear(file)
            TzTestRegistry.clearHighlighters()
        }
    }

    override fun update(e: AnActionEvent) {

        val file = CommonDataKeys.PSI_FILE.getData(e.dataContext)

        e.presentation.isEnabledAndVisible =
            e.project?.cucumberExecutionTracker()?.progressionGuides?.isNotEmpty() == true
                    || file != null && TzTestRegistry.hasResults(file)

    }
}
