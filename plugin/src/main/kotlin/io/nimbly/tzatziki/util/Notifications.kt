package io.nimbly.tzatziki.util

import com.intellij.notification.*
import com.intellij.notification.NotificationDisplayType.BALLOON
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