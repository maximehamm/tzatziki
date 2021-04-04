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

package io.nimbly.tzatziki.markdown

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDirectory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import icons.ActionIcons.ImagesFileType
import io.nimbly.tzatziki.psi.getDirectory
import io.nimbly.tzatziki.util.file
import io.nimbly.tzatziki.util.findFiles
import io.nimbly.tzatziki.util.getTextLineBefore
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

class TzPictureCompletion: CompletionContributor() {

    fun complete(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {

        // Check context
        val element = parameters.position
        if (element.node.elementType !== GherkinTokenTypes.TEXT) return

        val project = element.project
        val file = parameters.editor.file
            ?: return

        val root = ProjectFileIndex.SERVICE.getInstance(project).getSourceRootForFile(file.virtualFile)
            ?: return

        val directory = root.getDirectory(project)
        if (directory !is PsiDirectory)
            return

        // Check inside an image description
        if (null == REGX_IMG_MAKD.findAll(element.text)
                .toList()
                .find { result ->
                    val r = result.groups.last()!!.range
                    r.contains(parameters.offset - element.textOffset)
                }
        ) return

        val prefix = guessPrefix(parameters) ?: ""
        val before = parameters.editor.document.getTextLineBefore(parameters.offset).substringAfterLast("!").trimStart()
        val imgDescription = (Regex(" *\\[.*] *\\(").find(before)?.value ?: "[](").substringAfter('[')

        root.findFiles("gif", "png", "svg", scope = GlobalSearchScope.projectScope(project))
            .filter { !it.path.contains("/.cucumber+/")}
            .forEach {

                val id = it.path.substring(root.path.length + 1)
                val lookup = LookupElementBuilder.create(prefix + id)
                    .withPresentableText(id)
                    .withTypeText(it.name)     // Display on right side
                    .withIcon(ImagesFileType)
                    .withInsertHandler { context, item ->
                        val editor = context.editor

                        // Delete to end of image descriotion
                        val imageEndOffset = parameters.editor.document.text.indexOf(')', context.tailOffset)
                        editor.document.deleteString(context.tailOffset, imageEndOffset)

                        // replace before insertion
                        editor.document.replaceString(context.startOffset, parameters.offset, imgDescription)
                    }
                resultSet.addElement(lookup)
        }


        //val text = position.safeText

    }

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet)
                    = complete(parameters, context, resultSet)
            }
        )
    }
}

@Suppress("UnstableApiUsage")
fun guessPrefix(parameters: CompletionParameters): String? {
    val position = parameters.position
    val offset = parameters.offset
    val range = position.textRange
    return CompletionData.findPrefixStatic(position, offset)
}
