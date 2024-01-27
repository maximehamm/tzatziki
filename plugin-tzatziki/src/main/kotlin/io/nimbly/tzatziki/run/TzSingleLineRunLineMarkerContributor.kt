/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki.run

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafElement
import io.nimbly.tzatziki.Tzatziki
import io.nimbly.tzatziki.psi.firstCell
import io.nimbly.tzatziki.psi.isHeader
import io.nimbly.tzatziki.psi.row
import io.nimbly.tzatziki.psi.table
import io.nimbly.tzatziki.util.findCucumberStepDefinitions
import io.nimbly.tzatziki.util.parentOfTypeIs
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell

//@see https://github.com/JetBrains/intellij-plugins/tree/master/cucumber/src/org/jetbrains/plugins/cucumber/run
class TzSingleLineRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element !is LeafElement)
            return null

        if (element.containingFile !is GherkinFile)
            return null

        var cell = element.parent as? GherkinTableCell
        if (cell == null && element is PsiWhiteSpace && element.nextSibling is GherkinTableCell)
            cell = element.nextSibling as GherkinTableCell
        else return null

        if (cell.row.isHeader)
            return null

        if (cell.row.table.parent !is GherkinExamplesBlock)
            return null

        if (cell != cell.row.firstCell)
            return null

        val scenario = cell.parentOfTypeIs<GherkinStepsHolder>()
            ?: return null

        val definitions = scenario.findCucumberStepDefinitions()
        if (definitions.isEmpty())
            return null

        Tzatziki().extensionList.find { it.canRunStep(definitions) }
            ?: return null

        val state = getTestStateStorage(element)
        return getInfo(state)
    }

    private fun getTestStateStorage(element: PsiElement): TestStateStorage.Record? {
        val url = element.containingFile.virtualFile.url + ":" + CucumberUtil.getLineNumber(element)
        return TestStateStorage.getInstance(element.project).getState('"' + url + '"')
    }

    private fun getInfo(state: TestStateStorage.Record?)
        = Info(getTestStateIcon(state, false),
            ExecutorAction.getActions(1)) { "Run single line" }
}