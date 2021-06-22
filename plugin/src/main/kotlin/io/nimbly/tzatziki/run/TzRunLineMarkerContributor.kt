/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import io.nimbly.tzatziki.TZATZIKI
import io.nimbly.tzatziki.findCucumberStepDefinitions
import io.nimbly.tzatziki.psi.*
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.psi.*

//@see https://github.com/JetBrains/intellij-plugins/tree/master/cucumber/src/org/jetbrains/plugins/cucumber/run
class TzRunLineMarkerContributor : RunLineMarkerContributor() {

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

        val scenario = cell.parentOfType<GherkinStepsHolder>()
            ?: return null

        val definitions = findCucumberStepDefinitions(scenario)
        if (definitions.isEmpty())
            return null

        TZATZIKI().extensionList.find { it.canRunStep(definitions) }
            ?: return null

        val state = getTestStateStorage(element)
        return getInfo(state)
    }

    private fun getTestStateStorage(element: PsiElement): TestStateStorage.Record? {
        val url = element.containingFile.virtualFile.url + ":" + CucumberUtil.getLineNumber(element)
        return TestStateStorage.getInstance(element.project).getState('"' + url + '"')
    }

    private fun getInfo(state: TestStateStorage.Record?): Info {
        return withExecutorActions(getTestStateIcon(state, false))
    }
}