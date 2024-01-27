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

package io.nimbly.tzatziki.markdown

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.patterns.CharPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiDirectory
import com.intellij.util.ProcessingContext
import icons.ActionIcons.ImagesFileType
import io.nimbly.tzatziki.util.file
import io.nimbly.tzatziki.util.findFiles
import io.nimbly.tzatziki.util.getDirectory
import io.nimbly.tzatziki.util.safeText
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
        fun Regex.find(): String? {
            findAll(element.safeText)
                .toList()
                .mapNotNull { it.groups.last() }
                .forEach {
                    val r = it.range
                    if (r.contains(parameters.offset - element.textOffset) || r.isEmpty() && parameters.offset - element.textOffset == r.first)
                        return  element.safeText.substring(r.first, parameters.offset - element.textOffset)  //it.value
                }
            return null
        }

        val imageMd = Regex("!\\[(.*?)]\\((.*?)\\)").find()
        val imageHTml = Regex("<img +src *= *['\"]([a-z0-9-_:./]*)['\"]", RegexOption.IGNORE_CASE).find()

        if (imageMd == null && imageHTml == null)
            return

        val filePrefix  = imageMd ?: imageHTml!!

        val prefix = guessPrefix(parameters) ?: ""
        val imgDescription =
            if (imageMd != null) {
                prefix.substringBefore('(') + '('
            } else {
                prefix.subSequence(0, prefix.lastIndexOfAny(charArrayOf('\'', '"')) + 1)
            }

        root.findFiles("gif", "png", "svg", project = project)
            .filter { !it.path.contains("/.cucumber+/") }
            .filter { it.path.substring(root.path.length + 1).startsWith(filePrefix) }
            .forEach {

                val id = it.path.substring(root.path.length + 1)
                val lookup = LookupElementBuilder.create(prefix + id)
                    .withPresentableText(id)
                    .withTypeText(it.name)     // Display on right side
                    .withIcon(ImagesFileType)
                    .withInsertHandler { context, item ->
                        val editor = context.editor

                        // Delete to end of image description
                        val imageEndOffset =
                            if (imageMd != null)
                                parameters.editor.document.text.indexOf(')', context.tailOffset)
                            else
                                parameters.editor.document.text.substring(context.tailOffset)
                                    .indexOfFirst { it == '\'' || it == '"' } + context.tailOffset

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

fun guessPrefix(parameters: CompletionParameters): String? {
    val insertedElement = parameters.position
    val offset = parameters.offset

    var substr = insertedElement.text.substring(0, offset - insertedElement.textRange.startOffset)
    if (substr.isEmpty() || Character.isWhitespace(substr[substr.length - 1])) return ""

    substr = substr.trim { it <= ' ' }

    var i = 0
    while (substr.length > i && StandardPatterns.not(CharPattern.javaIdentifierPartCharacter()).accepts(substr[i])) i++
    return substr.substring(i).trim { it <= ' ' }
}
