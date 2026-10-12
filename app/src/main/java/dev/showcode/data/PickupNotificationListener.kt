package dev.showcode.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.showcode.app
import dev.showcode.core.Source
import dev.showcode.core.ownerOfForward
import dev.showcode.core.parse.IncomingMessage

/**
 * 通知通道。两条理由让它和短信同等重要：
 * 有些驿站根本不发短信、只在自家 App 推通知；而妈妈经微信转发的取件码也只能从这里进来。
 */
class PickupNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = listOf(title, bigText.ifBlank { text }).filter { it.isNotBlank() }.joinToString("，")
        if (body.isBlank()) return
        app.repository.ingest(
            IncomingMessage(
                sender = senderLabel(sbn.packageName),
                body = body,
                receivedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                channel = Source.NOTIFICATION,
                owner = ownerOfForward(sbn.packageName),
            )
        )
    }

    private fun senderLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
