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

package io.nimbly.tzatziki.util

import com.intellij.ide.BrowserUtil
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.config.getRootCustomProperty
import io.nimbly.tzatziki.config.setRootCustomProperty
import java.util.*

private const val MIN_DELAY = 1000*60*10 // 10 minutes
private const val REVIEW = "plugin.ask.review"

private var firstUse: Date? = null

private var isPdfUsed: Boolean = false
private var isFormattingTableUsed: Boolean = false
private var isCopyPasteUsed: Boolean = false

private var isAlreadyNotified: Boolean = false

fun pdfUsed(origin: PsiElement) {
    isPdfUsed = true
    askToVote(origin)
}

fun formattingTableUsed(origin: PsiElement) {
    isFormattingTableUsed = true
    askToVote(origin)
}

fun copyPasteUsed(origin: PsiElement) {
    isCopyPasteUsed = true
    askToVote(origin)
}

fun askToVote(origin: PsiElement) {
    if (firstUse == null)
        firstUse = Date()

    //println("""
    //    ASK TO VOTE !
    //     isAlreadyNotified = $isAlreadyNotified
    //     isPdfUsed = $isPdfUsed
    //     isFormattingTableUsed = $isFormattingTableUsed
    //     isCopyPasteUsed = $isCopyPasteUsed""".trimIndent())

    if (isAlreadyNotified || !isPdfUsed || !isFormattingTableUsed || !isCopyPasteUsed)
        return

    if (Date().time - firstUse!!.time < MIN_DELAY)
        return

    isAlreadyNotified = true

    if ("False".equals(getRootCustomProperty(origin, REVIEW), true))
        return

    origin.project.notification(
        """Thank you for using Cucumber+ !<br/>
                <table width='100%' cellspacing='0' cellpadding='0'><tr>
                   <td><a href='REVIEW'>Review</a></td>
                   <td><a href='BUGTRACKER'>Submit a bug or suggestion</a></td>
                   <td><a href='DISMISS'>Dismiss</a></td>
                </tr></table>""") {
        when (it) {
            "REVIEW" -> BrowserUtil.browse("https://plugins.jetbrains.com/plugin/16289-cucumber-")
            "BUGTRACKER" -> BrowserUtil.browse("https://github.com/maximehamm/tzatziki/issues")
            "DISMISS" -> dismiss(origin)
        }
    }
}

fun dismiss(origin: PsiElement) {
    setRootCustomProperty(origin, REVIEW, "false")
}

