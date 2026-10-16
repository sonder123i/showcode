package dev.showcode.data

import android.content.Context
import android.content.SharedPreferences
import dev.showcode.core.HOUR_MS
import dev.showcode.core.FAMILY_LABEL_DEFAULT
import dev.showcode.core.UrgencyThresholds

/**
 * 轻量偏好。只有四个键，不值得为它引 DataStore 或 Room。
 * 阈值必须同步可读：urgencyOf 在组件的 onUpdate 里跑，那里没有协程上下文。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 丰巢临期阈值（小时）。frame 锁定默认 6、可调 2/4/6/8。 */
    var lockerDueSoonHours: Int
        get() = sp.getInt(KEY_LOCKER_HOURS, DEFAULT_LOCKER_HOURS)
        set(value) = sp.edit().putInt(KEY_LOCKER_HOURS, value.coerceIn(LOCKER_OPTIONS.min(), LOCKER_OPTIONS.max())).apply()

    /** 驿站临期阈值（分钟）。frame 锁定默认 90、可调 30/60/90/120。 */
    var stationDueSoonMinutes: Int
        get() = sp.getInt(KEY_STATION_MINUTES, DEFAULT_STATION_MINUTES)
        set(value) = sp.edit().putInt(KEY_STATION_MINUTES, value.coerceIn(STATION_OPTIONS.min(), STATION_OPTIONS.max())).apply()

    val thresholds: UrgencyThresholds
        get() = UrgencyThresholds(
            lockerDueSoonMs = lockerDueSoonHours * HOUR_MS,
            stationDueSoonMinutes = stationDueSoonMinutes,
        )

    /** 首启引导是否走完（含跳过）。老用户升级不重放引导。 */
    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** 首页「长按组件可只显示一个点」横幅是否已关闭（story-3 AC5：可关闭）。 */
    /**
     * 代取件分组的名字（story-7 AC2：用户能改）。「帮妈妈取」只是默认值——
     * 有人帮妈妈、有人帮室友，写死成一个称呼等于替用户决定了他的家庭结构。
     */
    var familyLabel: String
        get() = sp.getString(KEY_FAMILY_LABEL, null)?.takeIf { it.isNotBlank() } ?: FAMILY_LABEL_DEFAULT
        set(value) = sp.edit().putString(KEY_FAMILY_LABEL, value.trim()).apply()

    var widgetHintDismissed: Boolean
        get() = sp.getBoolean(KEY_WIDGET_HINT_DISMISSED, false)
        set(value) = sp.edit().putBoolean(KEY_WIDGET_HINT_DISMISSED, value).apply()

    /** 组件绑定的取件点 key；null = 通用组件（形态 B）。 */
    fun widgetBinding(widgetId: Int): String? = sp.getString(keyBinding(widgetId), null)

    fun widgetBindingName(widgetId: Int): String? = sp.getString(keyBindingName(widgetId), null)

    fun setWidgetBinding(widgetId: Int, pointKey: String?, pointName: String?) {
        sp.edit().apply {
            if (pointKey == null) {
                remove(keyBinding(widgetId))
                remove(keyBindingName(widgetId))
            } else {
                putString(keyBinding(widgetId), pointKey)
                putString(keyBindingName(widgetId), pointName ?: pointKey)
            }
        }.apply()
    }

    fun clearWidgetBinding(widgetId: Int) = setWidgetBinding(widgetId, null, null)

    /**
     * 巡检提醒的开关。默认开——但「开」离「会响」还差两道：必须**证实过推送盲区**
     * （见 `core/BlindSpot`）并且通知权限已给。所以默认开不等于默认吵人。
     */
    var reminderOn: Boolean
        get() = sp.getBoolean(KEY_REMINDER_ON, true)
        set(value) = sp.edit().putBoolean(KEY_REMINDER_ON, value).apply()

    /** 上次提醒所在的自然日（epochDay）。0 = 从没提醒过，第一次的文案要解释「为什么开始提醒你」。 */
    var lastRemindedDay: Int
        get() = sp.getInt(KEY_REMINDED_DAY, 0)
        set(value) = sp.edit().putInt(KEY_REMINDED_DAY, value).apply()

    /** 那条一次性询问问过了没有。问过就不再问——反复问同一件事比不问更让人烦。 */
    var reminderAsked: Boolean
        get() = sp.getBoolean(KEY_REMINDER_ASKED, false)
        set(value) = sp.edit().putBoolean(KEY_REMINDER_ASKED, value).apply()

    /**
     * 底栏要不要玻璃。默认开——但「开」离「真的有玻璃」还差一件：平台能力（API 31+，blur 要
     * RenderEffect）。见 `ui/glass/GlassCompat` 那条梯子。
     */
    var glassBarOn: Boolean
        get() = sp.getBoolean(KEY_GLASS_BAR_ON, true)
        set(value) = sp.edit().putBoolean(KEY_GLASS_BAR_ON, value).apply()

    /**
     * 本次（或上一次）进程是否真的上过玻璃。给 `GlassRuntimeGuard` 当判据用：
     * 只有「上一个进程失败」**且**「那时开着玻璃」才算一次与玻璃有关的失败。
     * 冷启动时由 guard 擦掉，正常退回后台**不擦**——家族也是这么做的。
     */
    var glassSessionUsed: Boolean
        get() = sp.getBoolean(KEY_GLASS_SESSION_USED, false)
        set(value) = sp.edit().putBoolean(KEY_GLASS_SESSION_USED, value).apply()

    private fun keyBinding(widgetId: Int) = "widget_bind_$widgetId"
    private fun keyBindingName(widgetId: Int) = "widget_bind_name_$widgetId"

    fun restoreThresholdDefaults() {
        sp.edit()
            .putInt(KEY_LOCKER_HOURS, DEFAULT_LOCKER_HOURS)
            .putInt(KEY_STATION_MINUTES, DEFAULT_STATION_MINUTES)
            .apply()
    }

    companion object {
        private const val NAME = "showcode_prefs"
        private const val KEY_LOCKER_HOURS = "locker_due_soon_hours"
        private const val KEY_STATION_MINUTES = "station_due_soon_minutes"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_WIDGET_HINT_DISMISSED = "widget_hint_dismissed"
        private const val KEY_FAMILY_LABEL = "family_label"
        private const val KEY_REMINDER_ON = "pickup_reminder_on"
        private const val KEY_REMINDED_DAY = "pickup_reminded_day"
        private const val KEY_REMINDER_ASKED = "pickup_reminder_asked"
        private const val KEY_GLASS_BAR_ON = "glass_bar_on"
        private const val KEY_GLASS_SESSION_USED = "glass_session_used"

        const val DEFAULT_LOCKER_HOURS = 6
        const val DEFAULT_STATION_MINUTES = 90

        /** frame 锁定的可调档位，不多不少。 */
        val LOCKER_OPTIONS = listOf(2, 4, 6, 8)
        val STATION_OPTIONS = listOf(30, 60, 90, 120)
    }
}
