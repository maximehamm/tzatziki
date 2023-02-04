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

package io.nimbly.tzatziki.util

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker.getNotificationGroup

fun Project.notification(
    text: String,
    notificationType: NotificationType = NotificationType.INFORMATION,
    function: ((event: String) -> Any?)? = null) {

    val n = getNotificationGroup().createNotification(
        title = TZATZIKI_NAME,
        content = "<html>$text</html>",
        type = notificationType
    )

    n.addAction(NotificationAction.create {
        if (function!=null)
            function(it.place)
        n.expire()
//    {
//            notification: Notification, event: HyperlinkEvent ->
//        if (function!=null)
//            function(event.description)
//        notification.expire();
//    }.notify(this)
    })

    n.notify(this)

}