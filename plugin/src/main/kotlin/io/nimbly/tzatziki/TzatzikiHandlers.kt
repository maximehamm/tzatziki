/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result.CONTINUE
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result.STOP
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.psi.format
import io.nimbly.tzatziki.testdiscovery.TzTestRegistry
import io.nimbly.tzatziki.util.addNewColum
import io.nimbly.tzatziki.util.findTableAt
import io.nimbly.tzatziki.util.getTextLine
import io.nimbly.tzatziki.util.stopBeforeDeletion
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class TzTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.gherkin && editor.document.getTextLine(editor.caretModel.offset).contains("|"))
            editor.findTableAt(editor.caretModel.offset)?.format()

        if (file.gherkin)
            TzTestRegistry.cleanTestsResults(file, editor)

        return CONTINUE
    }

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (!file.gherkin)
            return CONTINUE

        if (editor.addNewColum(c, project, fileType))
            return STOP

        return CONTINUE
    }

    override fun beforeSelectionRemoved(c: Char, project: Project, editor: Editor, file: PsiFile): Result {

        if (file.gherkin && editor.stopBeforeDeletion(false, false))
            return STOP

        return CONTINUE
    }

    private val PsiFile.gherkin: Boolean
        get() = TOGGLE_CUCUMBER_PL && fileType == GherkinFileType.INSTANCE
}