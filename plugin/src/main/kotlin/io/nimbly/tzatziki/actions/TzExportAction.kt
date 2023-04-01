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

package io.nimbly.tzatziki.actions

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import io.nimbly.tzatziki.pdf.*
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.*

class TzExportAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {

        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val vfiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (vfiles.isEmpty()) return

        try {
            ExportPdf(vfiles.toList(), project).exportFeatures()
        } catch (e: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotification("Please wait until index is ready")
        } catch (e: TzatzikiException) {
            project.notification(e.message ?: "$TZATZIKI_NAME error !", NotificationType.WARNING)
        } catch (e: Exception) {
            e.printStackTrace()
            project.notification(e.message ?: "$TZATZIKI_NAME error !", NotificationType.WARNING)
        }
    }

    override fun update(event: AnActionEvent) {

        // Selecting a gherkin file outside of source path
        val nav = event.getData(CommonDataKeys.NAVIGATABLE_ARRAY) as? Array<*>
        if (nav != null && nav.size == 1) {

            val f = nav[0] as? PsiFileNode
            if (f?.value?.fileType == GherkinFileType.INSTANCE) {
                event.presentation.isEnabledAndVisible = true
                event.presentation.text = "Export feature to PDF"
                super.update(event)
                return
            }
        }

        // Selecting from resource path
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val project = event.getData(CommonDataKeys.PROJECT)
        val vfiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val isGherkinFile = file?.fileType == GherkinFileType.INSTANCE

        var isVisible = vfiles!=null && (isGherkinFile || file == null)

        if (isVisible && project!=null) {

            // Check selected files all bellong to same root
            var root: VirtualFile? = null
            vfiles?.find {
                val r = ProjectFileIndex.SERVICE.getInstance(project).getSourceRootForFile(it)
                if (r == null || root!=null && r!=root) {
                    isVisible = false
                    true
                }
                else {
                    root =r
                    false
                }
            }
        }

        event.presentation.isEnabledAndVisible = isVisible
        event.presentation.text = "Export feature${if (isGherkinFile) "" else "s"} to PDF"
        super.update(event)
    }

    override fun isDumbAware()
            = true
}