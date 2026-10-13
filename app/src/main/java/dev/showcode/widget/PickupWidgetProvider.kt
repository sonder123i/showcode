package dev.showcode.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.showcode.MainActivity
import dev.showcode.R
import dev.showcode.app
import dev.showcode.data.unrecognizedBadgeCount
import dev.showcode.core.MINUTE_MS
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.Urgency
import dev.showcode.core.countdownOf
import dev.showcode.core.deadlineOf
import dev.showcode.core.nextCrossingAt
import dev.showcode.core.badgeOwnerId
import dev.showcode.core.groupByPoint
import dev.showcode.core.resolvePointKey
import dev.showcode.core.urgencyOf
import dev.showcode.ui.countdownLabel
import dev.showcode.ui.theme.AccentInt
import dev.showcode.ui.theme.UrgencyAtRiskInt
import dev.showcode.ui.theme.UrgencyDueSoonInt
import dev.showcode.ui.theme.UrgencySafeInt
import dev.showcode.ui.urgencyLabelRes

/**
 * 桌面组件（方案丙：1 个通用组件，绑定单点是一等公民）。
 * 手写 RemoteViews 而不是 Glance：176dp 两节、分节 minHeight 44 保点名这种像素级约束，
 * 用平台原生布局表达最直接，也不为本机依赖缓存再添一个未知数。
 *
 * 形态：A 未绑定且无数据 / B 通用分节（MVP 默认）/ C 绑定单点 / D 绑定点已清空。
 */
class PickupWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { context.app.repository.prefs.clearWidgetBinding(it) }
        updateAll(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) updateAll(context)
    }

    companion object {
        const val ACTION_TICK = "dev.showcode.widget.TICK"

        /** 倒计时的分钟级粒度。组件不是现场模式，不需要逐秒跳。 */
        private const val TICK_MS = 60_000L
        private const val STALE_MS = 120 * MINUTE_MS

        /** frame 锁定：最多 2 节，多出来的收进「+N 个点」。 */
        private const val MAX_SECTIONS = 2

        /** 形态 C 免点击概览的码行数；多出来的收进「+N」。 */
        private const val MAX_CODES = 2

        // PendingIntent 的槽位号：与 widgetId 组合成唯一 requestCode，避免互相覆盖
        private const val SLOT_TITLE = 0
        private const val SLOT_BADGE = 1
        private const val SLOT_MORE = 2
        private const val SLOT_EMPTY = 3
        private const val SLOT_SECTION_BASE = 4

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PickupWidgetProvider::class.java))
            val prefs = context.app.repository.prefs
            val badgeOwner = badgeOwnerId(ids.toList(), ids.filter { prefs.widgetBinding(it) != null }.toSet())
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(context, id, showBadge = id == badgeOwner))
            }
            scheduleTick(context, ids.isNotEmpty())
        }

        private fun buildViews(context: Context, widgetId: Int, showBadge: Boolean): RemoteViews {
            val repository = context.app.repository
            val data = repository.data.value
            val thresholds = repository.thresholds.value
            val now = System.currentTimeMillis()
            val prefs = context.app.repository.prefs

            val views = RemoteViews(context.packageName, R.layout.widget_root)
            val groups = groupByPoint(data.parcels, now, thresholds, data.aliases)
            /* 绑定点被合并掉之后，组件要跟着指向合并后的那个点：否则用户的桌面
               会在某天早上突然变成空态，而且不知道为什么。 */
            val boundKey = prefs.widgetBinding(widgetId)?.let { resolvePointKey(it, data.aliases) }
            val boundGroup = boundKey?.let { key -> groups.firstOrNull { it.key == key } }

            // stale-freshness：陈旧的码比没有码更危险。还没读完库时不报——
            // 那时候已经有「正在读取…」在说了，两条互相矛盾的文案比一条都没有更糟。
            val stale = data.loaded && (data.lastSyncAt == null || now - data.lastSyncAt > STALE_MS)
            views.setViewVisibility(R.id.widget_stale, if (stale) View.VISIBLE else View.GONE)

            val unrecognizedBadge = unrecognizedBadgeCount(data.unrecognized)
            if (showBadge && unrecognizedBadge > 0) {
                views.setViewVisibility(R.id.widget_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_badge, context.getString(R.string.widget_badge, unrecognizedBadge))
                views.setOnClickPendingIntent(
                    R.id.widget_badge,
                    pending(context, widgetId, SLOT_BADGE, WidgetNav.ROUTE_UNRECOGNIZED, null),
                )
            } else {
                views.setViewVisibility(R.id.widget_badge, View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_title_row, pending(context, widgetId, SLOT_TITLE, WidgetNav.ROUTE_HOME, null))
            views.setOnClickPendingIntent(R.id.widget_root, pending(context, widgetId, SLOT_TITLE, WidgetNav.ROUTE_HOME, null))

            views.removeAllViews(R.id.widget_sections)
            views.setViewVisibility(R.id.widget_sections, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_bound_empty, View.GONE)
            views.setViewVisibility(R.id.widget_more, View.GONE)

            when {
                /* 还没读完库。组件是用户不打开 App 也会看到的表面，
                   冷启动那一瞬骗他「没有快递」，他今天可能就真的不去取。
                   这一支必须在最前面：绑定点那条（形态 D）同样会被空数据误判成「你的点清空了」。 */
                !data.loaded -> {
                    views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                    views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_loading))
                    views.setViewVisibility(R.id.widget_empty_cta, View.GONE)
                    views.setOnClickPendingIntent(
                        R.id.widget_empty,
                        pending(context, widgetId, SLOT_EMPTY, WidgetNav.ROUTE_HOME, null),
                    )
                }

                // 形态 D：绑定点已清空。不能空白（以为坏了）也不能移除（下次还要用）
                boundKey != null && boundGroup == null -> {
                    views.setViewVisibility(R.id.widget_bound_empty, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_bound_empty_name,
                        prefs.widgetBindingName(widgetId) ?: boundKey,
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_bound_empty,
                        pending(context, widgetId, SLOT_EMPTY, WidgetNav.ROUTE_ONSITE, boundKey),
                    )
                }

                // 形态 C：绑定单点，免点击概览
                boundGroup != null -> {
                    views.setViewVisibility(R.id.widget_sections, View.VISIBLE)
                    views.addView(R.id.widget_sections, sectionViews(context, widgetId, boundGroup, now, SLOT_SECTION_BASE))
                    boundGroup.items.take(MAX_CODES).forEachIndexed { index, parcel ->
                        val row = RemoteViews(context.packageName, R.layout.widget_code_row)
                        row.setTextViewText(R.id.row_code, parcel.code)
                        if (parcel.lockerNo != null) {
                            row.setViewVisibility(R.id.row_locker, View.VISIBLE)
                            row.setTextViewText(R.id.row_locker, parcel.lockerNo)
                        }
                        row.setOnClickPendingIntent(
                            R.id.row_root,
                            pending(context, widgetId, SLOT_SECTION_BASE + 1 + index, WidgetNav.ROUTE_ONSITE, boundGroup.key),
                        )
                        views.addView(R.id.widget_sections, row)
                    }
                    val rest = boundGroup.items.size - MAX_CODES
                    if (rest > 0) {
                        views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                        views.setTextViewText(R.id.widget_more, context.getString(R.string.widget_more_items, rest))
                        views.setOnClickPendingIntent(
                            R.id.widget_more,
                            pending(context, widgetId, SLOT_MORE, WidgetNav.ROUTE_ONSITE, boundGroup.key),
                        )
                    }
                }

                // 形态 A：未绑定且无数据。组件版的 empty-first-time，点进演示而不是空首页
                groups.isEmpty() -> {
                    views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_empty_text,
                        context.getString(R.string.widget_empty_line1) + "\n" + context.getString(R.string.widget_empty_line2),
                    )
                    views.setTextColor(R.id.widget_empty_cta, AccentInt)
                    views.setOnClickPendingIntent(
                        R.id.widget_empty,
                        pending(context, widgetId, SLOT_EMPTY, WidgetNav.ROUTE_DEMO, null),
                    )
                }

                // 形态 B：通用组件，按取件点分节
                else -> {
                    views.setViewVisibility(R.id.widget_sections, View.VISIBLE)
                    groups.take(MAX_SECTIONS).forEachIndexed { index, group ->
                        views.addView(
                            R.id.widget_sections,
                            sectionViews(context, widgetId, group, now, SLOT_SECTION_BASE + index),
                        )
                    }
                    val rest = groups.size - MAX_SECTIONS
                    if (rest > 0) {
                        views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                        views.setTextViewText(R.id.widget_more, context.getString(R.string.widget_more, rest))
                        views.setOnClickPendingIntent(
                            R.id.widget_more,
                            pending(context, widgetId, SLOT_MORE, WidgetNav.ROUTE_HOME, null),
                        )
                    }
                }
            }
            return views
        }

        private fun sectionViews(
            context: Context,
            widgetId: Int,
            group: PickupPointGroup,
            now: Long,
            slot: Int,
        ): RemoteViews {
            val section = RemoteViews(context.packageName, R.layout.widget_section)
            val color = when (group.worst) {
                Urgency.SAFE -> UrgencySafeInt
                Urgency.DUE_SOON -> UrgencyDueSoonInt
                Urgency.AT_RISK -> UrgencyAtRiskInt
            }
            section.setInt(R.id.section_bar, "setBackgroundColor", color)
            section.setTextViewText(R.id.section_name, group.name)
            section.setTextViewText(R.id.section_count, group.items.size.toString())

            // 与 mockup 一致：临期显示逐分钟变化的倒计时，过期风险显示档位名，安全不占位
            val worstItem = group.items.first()
            val tick = when (group.worst) {
                Urgency.DUE_SOON -> deadlineOf(worstItem, now)?.let { countdownLabel(context, countdownOf(it - now)) }
                Urgency.AT_RISK -> context.getString(urgencyLabelRes(Urgency.AT_RISK))
                Urgency.SAFE -> null
            }
            if (tick == null) {
                section.setViewVisibility(R.id.section_tick, View.GONE)
            } else {
                section.setViewVisibility(R.id.section_tick, View.VISIBLE)
                section.setTextViewText(R.id.section_tick, tick)
                section.setTextColor(R.id.section_tick, color)
            }

            // tap 分节 = 声明「我在这个点」→ 1 步直进该点现场模式（可度量成功标准）
            section.setOnClickPendingIntent(
                R.id.section_root,
                pending(context, widgetId, slot, WidgetNav.ROUTE_ONSITE, group.key),
            )
            return section
        }

        private fun pending(context: Context, widgetId: Int, slot: Int, route: String, pointKey: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WidgetNav.EXTRA_ROUTE, route)
                if (pointKey != null) putExtra(WidgetNav.EXTRA_POINT_KEY, pointKey)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 16 + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * 三档唤醒，全部由「有没有数字在动」决定，不空转耗电：
         * - 有临期件：60 秒重复 tick，倒计时要跟着走；
         * - 没有临期件但有未来的跨界时刻：一次性闹钟醒在那一刻。
         *   以前这一档什么都没有——「安全 → 临期」没有任何唤醒被安排，
         *   桌面要等到下一次 30 分钟系统周期才跟上，而临期提醒正是这个 App 的差异化；
         * - 两件都没有：取消。
         *
         * 用 `setAndAllowWhileIdle` 而不是精确闹钟：不需要 `SCHEDULE_EXACT_ALARM` 权限，
         * 也不该在国产 ROM 上承诺精确。醒来后 `updateAll` 会重算并再挂下一次。
         */
        private fun scheduleTick(context: Context, hasWidgets: Boolean) {
            val repository = context.app.repository
            val data = repository.data.value
            val thresholds = repository.thresholds.value
            val now = System.currentTimeMillis()
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val tick = tickPending(context)
            val countingDown = hasWidgets && data.parcels.any {
                !it.picked && urgencyOf(it, now, thresholds) == Urgency.DUE_SOON
            }
            val crossing = if (hasWidgets) nextCrossingAt(data.parcels, now, thresholds) else null
            when {
                countingDown -> alarm.setInexactRepeating(AlarmManager.RTC, now + TICK_MS, TICK_MS, tick)
                crossing != null -> {
                    alarm.cancel(tick)
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC, crossing, tick)
                }
                else -> alarm.cancel(tick)
            }
        }

        private fun cancelTick(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(tickPending(context))
        }

        private fun tickPending(context: Context) = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PickupWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
