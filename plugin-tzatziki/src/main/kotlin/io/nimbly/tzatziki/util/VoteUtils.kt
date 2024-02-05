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

package io.nimbly.tzatziki.util

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

const val TZATZIKI_PLUGIN = "io.nimbly.tzatziki"

fun askToVoteLinkedIn(project: Project) {

    val currentVersion = PluginManagerCore.getPlugin(PluginId.getId(TZATZIKI_PLUGIN))?.version ?: ""
    val version = PropertiesComponent.getInstance().getValue(TZATZIKI_PLUGIN)

//    if (version != currentVersion) {
//
//        // Plugin was updated... 'looks like your ready to vote !
//        project.notification(
//            """Would you comment or like my <a href='LINKEDIN'>LinkedIn post</a> celebrating 100,000 downloads 😍?
//                    <br/>
//                    <table width='100%' cellspacing='0' cellpadding='0'><tr>
//                       <td><a href='REVIEW'>Review</a></td>
//                       <td><a href='BUGTRACKER'>Submit a bug or suggestion</a></td>
//                       <td><a href='DISMISS'>Dismiss</a></td>
//                    </tr></table>""") {
//            when (it) {
//                "LINKEDIN" -> BrowserUtil.browse("https://www.linkedin.com/pulse/wow-cucumber-passed-100000-downloads-mark-today-maxime-hamm/")
//                "REVIEW" -> BrowserUtil.browse("https://plugins.jetbrains.com/plugin/16289-cucumber-")
//                "BUGTRACKER" -> BrowserUtil.browse("https://github.com/maximehamm/tzatziki/issues")
//            }
//        }
//
//        PropertiesComponent.getInstance().setValue(TZATZIKI_PLUGIN, currentVersion)
//    }
}

fun askToVote(project: Project) {

    val currentVersion = PluginManagerCore.getPlugin(PluginId.getId(TZATZIKI_PLUGIN))?.version ?: ""
    val version = PropertiesComponent.getInstance().getValue(TZATZIKI_PLUGIN)

    if (version == null) {

        // First time used : do just save the revision number
        PropertiesComponent.getInstance().setValue(TZATZIKI_PLUGIN, currentVersion)
    }
    else if (version != currentVersion) {

//        // Plugin was updated... 'looks like your ready to vote !
//        project.notification(
//            """Thank you for using $TZATZIKI_NAME !<br/>
//                    <table width='100%' cellspacing='0' cellpadding='0'><tr>
//                       <td><a href='REVIEW'>Review</a></td>
//                       <td><a href='BUGTRACKER'>Submit a bug or suggestion</a></td>
//                       <td><a href='DISMISS'>Dismiss</a></td>
//                    </tr></table>""") {
//            when (it) {
//                "REVIEW" -> BrowserUtil.browse("https://plugins.jetbrains.com/plugin/16289-cucumber-")
//                "BUGTRACKER" -> BrowserUtil.browse("https://github.com/maximehamm/tzatziki/issues")
//            }
//        }

        project.notificationAction("Thank you for using $TZATZIKI_NAME !", NotificationType.INFORMATION,
            mapOf(
                "Review" to { BrowserUtil.browse("https://plugins.jetbrains.com/plugin/16289-cucumber-") },
                "Submit a bug or suggestion" to { BrowserUtil.browse("https://github.com/maximehamm/tzatziki/issues") },
            )
        )

        PropertiesComponent.getInstance().setValue(TZATZIKI_PLUGIN, currentVersion)
    }
}

