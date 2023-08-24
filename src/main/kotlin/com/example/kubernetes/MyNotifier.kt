package com.example.kubernetes

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project


object MyNotifier {
    fun notifyInformationMessage(project: Project?, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Custom Notification Group")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }
}