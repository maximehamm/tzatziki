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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType.BALLOON
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project


private val NOTIFICATION_GROUP =
    NotificationGroup("Cucumber +", BALLOON, true)

// Post 2020.3
//private val NOTIFICATION_GROUP =
//    NotificationGroupManager.getInstance().getNotificationGroup("io.nimbly.notification.group")

private var LAST_NOTIFICATION: Notification? = null

fun info(message: String, project: Project) {
    notify(message, NotificationType.INFORMATION, project)
}

fun warn(message: String, project: Project) {
    notify(message, NotificationType.WARNING, project)
}

fun error(message: String, project: Project) {
    notify(message, NotificationType.ERROR, project)
}

fun notify(message: String, type: NotificationType, project: Project) {
    val success = NOTIFICATION_GROUP.createNotification("Cucumber+", message, type)
    LAST_NOTIFICATION = success
    Notifications.Bus.notify(success, project)
}

fun lastNotification()
    = LAST_NOTIFICATION?.content ?: ""

fun resetLastNotification() {
    LAST_NOTIFICATION = null
}