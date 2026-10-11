package dev.showcode.data

import android.content.Context
import android.content.SharedPreferences
import dev.showcode.core.HOUR_MS
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

        const val DEFAULT_LOCKER_HOURS = 6
        const val DEFAULT_STATION_MINUTES = 90

        /** frame 锁定的可调档位，不多不少。 */
        val LOCKER_OPTIONS = listOf(2, 4, 6, 8)
        val STATION_OPTIONS = listOf(30, 60, 90, 120)
    }
}
