package dev.showcode.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.showcode.MainActivity
import dev.showcode.R
import dev.showcode.app
import dev.showcode.core.BlindSpot
import dev.showcode.core.Source
import dev.showcode.widget.WidgetNav

/**
 * 巡检提醒：每天早上叫一次「去拼多多/淘宝那一栏对一眼」。
 *
 * 它存在的唯一理由是一个绕不开的事实——**漏件发生在你不打开亮码的时候**。
 * 有些件不发短信、不推通知，只在平台自己的列表里露一下（见 `core/BlindSpot`），
 * 所以任何只在 App 内的提示都治不了漏，只能治「已经想起来看之后太麻烦」。
 *
 * 三道闸，缺一不响：
 * ① 证实过盲区（用户手动录过件）；② 开关开着；③ 今天还没提醒过。
 * 外加通知权限没给就**一条都不发**——那时退化成首页那条常驻提示，功能不损失，只是不主动。
 *
 * 调度用 AlarmManager 而不是引 WorkManager：这个仓库不新增依赖，
 * 而组件那套唤醒已经在用同一个机制（`setAndAllowWhileIdle`，不需要精确闹钟权限）。
 */
object PickupReminder {

    const val CHANNEL_ID = "pickup-check"

    /** 闹钟的 requestCode：和组件的 tick 分开，互不覆盖。 */
    private const val REQUEST_CODE = 71

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_desc)
            },
        )
    }

    fun permissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 挂下一次。永远重挂——包括「今天不该提醒」的那些日子。
     *
     * ⚠️ 不能在条件不满足时就不挂了事：那样用户哪天第一次手动导入了一条件，
     * 闹钟并不在场，提醒要等到下次开 App 才可能被挂上。挂闹钟是便宜的（不精确、系统可合并），
     * **要不要响**才是要判的那件事，而那一步在 [fire] 里。
     */
    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC,
            BlindSpot.nextReminderAt(System.currentTimeMillis()),
            pending(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    /** 闹钟醒了：过三道闸，该响才响，然后把自己排到明天。 */
    fun fire(context: Context) {
        val repository = context.app.repository
        val prefs = repository.prefs
        val now = System.currentTimeMillis()
        val today = BlindSpot.epochDay(now)
        val sources = repository.distinctSources()
        try {
            if (!BlindSpot.shouldRemind(sources, prefs.reminderOn, prefs.lastRemindedDay, today)) return
            if (!permissionGranted(context)) return
            notify(context, BlindSpot.isFirstReminder(prefs.lastRemindedDay))
            prefs.lastRemindedDay = today
        } finally {
            schedule(context)
        }
    }

    private fun notify(context: Context, first: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(
            REQUEST_CODE,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_identity)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(if (first) R.string.reminder_body_first else R.string.reminder_body))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    context.getString(if (first) R.string.reminder_body_first else R.string.reminder_body),
                ))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        REQUEST_CODE,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(WidgetNav.EXTRA_ROUTE, WidgetNav.ROUTE_IDENTITY)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PickupReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class PickupReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PickupReminder.fire(context)
    }
}
