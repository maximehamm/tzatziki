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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker.getNotificationGroup
import javax.swing.event.HyperlinkEvent

//fun Project.notification(
//    text: String,
//    notificationType: NotificationType = NotificationType.INFORMATION,
//    function: ((event: String) -> Any?)? = null) {
//
//    val notif = getNotificationGroup().createNotification("<html>$text</html>", notificationType)
//    if (function != null) {
//        notif.setListener(object : NotificationListener() {
//            override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
//                function(event.description)
//            }
//        })
//    }
//    notif.notify(this)
//
//    getNotificationGroup().createNotification(
//        TZATZIKI_NAME, "<html>$text</html>", notificationType) { notification: Notification, event: HyperlinkEvent ->
//        if (function!=null)
//            function(event.description)
//        notification.expire();
//    }.notify(this)
//}



fun Project.notification(
    text: String,
    notificationType: NotificationType = NotificationType.INFORMATION) {

    getNotificationGroup()
        .createNotification(TZATZIKI_NAME, text, notificationType)
        .notify(this)
}

fun Project.notificationAction(
    text: String,
    notificationType: NotificationType = NotificationType.INFORMATION,
    actions: Map<String, (() -> Any?)>) {

    val notif = getNotificationGroup()
        .createNotification(TZATZIKI_NAME, text, notificationType)

    actions.forEach { (actionText, function) ->
        notif.addAction(object : AnAction(actionText) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) {
                function()
                notif.expire();
            }
         })
    }

    notif.notify(this);
}
