package com.example.kubernetes

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project


object MyNotifier {
    fun notifyInformationMessage(project: Project?, content: String, subtitle: String? = null) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Custom Notification Group")
            .createNotification(content, NotificationType.INFORMATION)
            .setSubtitle(subtitle)
            .notify(project)


    }
}