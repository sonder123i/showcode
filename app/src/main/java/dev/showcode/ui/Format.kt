package dev.showcode.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.annotation.DrawableRes
import dev.showcode.R
import dev.showcode.core.Countdown
import dev.showcode.core.MINUTE_MS
import dev.showcode.core.Parcel
import dev.showcode.core.Owner
import dev.showcode.core.PointKind
import dev.showcode.core.Urgency
import dev.showcode.core.countdownOf
import dev.showcode.core.deadlineOf
import dev.showcode.ui.theme.UrgencyAtRisk
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

fun urgencyColor(urgency: Urgency): Color = when (urgency) {
    Urgency.SAFE -> UrgencySafe
    Urgency.DUE_SOON -> UrgencyDueSoon
    Urgency.AT_RISK -> UrgencyAtRisk
}

fun urgencyLabelRes(urgency: Urgency): Int = when (urgency) {
    Urgency.SAFE -> R.string.urgency_safe
    Urgency.DUE_SOON -> R.string.urgency_due_soon
    Urgency.AT_RISK -> R.string.urgency_at_risk
}

@Composable
fun urgencyLabel(urgency: Urgency): String = stringResource(urgencyLabelRes(urgency))

@Composable
fun pointKindLabel(kind: PointKind): String = stringResource(
    when (kind) {
        PointKind.STATION -> R.string.kind_station
        PointKind.LOCKER -> R.string.kind_locker
        PointKind.SELFPICK -> R.string.kind_selfpick
    }
)

/** 柜号区的标签：柜子叫「格口」，驿站与自提点叫「货架」。 */
@Composable
fun lockerLabelOf(kind: PointKind): String = stringResource(
    if (kind == PointKind.LOCKER) R.string.onsite_locker_label else R.string.onsite_shelf_label
)

fun formatMinuteOfDay(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@Composable
fun closingLabel(minuteOfDay: Int): String =
    stringResource(R.string.closing_label, formatMinuteOfDay(minuteOfDay))

/**
 * 时效文案。临期态返回**逐秒变化**的倒计时——frame 锁定的 urgency_gradient 就落在这里：
 * 视觉只有三态三色，紧迫度的连续梯度全部由这个数字承载，所以它不能是一句静态的「临期」。
 *
 * 安全态返回 null：不显示时效行。与 H5 参照一致（安全件没有 deadlineLabel，那一块整个不渲染）。
 */
@Composable
fun deadlineText(parcel: Parcel, urgency: Urgency, now: Long): String? {
    val deadline = deadlineOf(parcel, now)
    return when (urgency) {
        Urgency.SAFE -> null
        Urgency.DUE_SOON -> if (deadline == null) null else countdownText(countdownOf(deadline - now))
        Urgency.AT_RISK -> when (parcel.pointKind) {
            PointKind.LOCKER -> stringResource(
                // 丰巢的收费规则是核实过的（18 小时免费，之后 0.5 元/12 小时，上限 3 元）；
                // 别家柜机不是这个价，所以只有在能确认是丰巢时才报具体数字。
                if (parcel.raw.contains("丰巢") || parcel.pointName.contains("丰巢")) {
                    R.string.onsite_overdue_fengchao
                } else {
                    R.string.onsite_overdue_locker
                }
            )
            PointKind.STATION -> stringResource(R.string.onsite_station_closed)
            PointKind.SELFPICK -> stringResource(R.string.onsite_selfpick_closed)
        }
    }
}

@Composable
fun countdownText(c: Countdown): String = if (c.usesHours) {
    stringResource(R.string.countdown_hours, c.hours, c.minutes.toString().padStart(2, '0'))
} else {
    stringResource(R.string.countdown_minutes, c.minutes, c.seconds.toString().padStart(2, '0'))
}

/**
 * 桌面组件用的同一套倒计时文案。RemoteViews 不在组合上下文里拿不到 stringResource，
 * 但规则必须和 App 内完全一致：有小时只显示到分，没小时才显示到秒。
 */
fun countdownLabel(context: Context, c: Countdown): String = if (c.usesHours) {
    context.getString(R.string.countdown_hours, c.hours, c.minutes.toString().padStart(2, '0'))
} else {
    context.getString(R.string.countdown_minutes, c.minutes, c.seconds.toString().padStart(2, '0'))
}

fun relativeTime(timestamp: Long, now: Long): String = DateUtils
    .getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    .toString()

/**
 * 带「刚刚」兜底的相对时间。平台 API 对 1 分钟内的差值会给出「0分钟后」，
 * 而现场模式的空态锚定行「最近一次取件：…」恰恰最容易撞上这一分钟。
 */
@Composable
fun relativeTimeText(timestamp: Long, now: Long): String =
    if (kotlin.math.abs(timestamp - now) < MINUTE_MS) stringResource(R.string.time_just_now)
    else relativeTime(timestamp, now)

/**
 * 会走的当前时刻。
 * [intervalMs] 由调用方按需要给：现场模式有临期件时给 1 秒（倒计时要逐秒变），
 * 没有临期件时给 30 秒就够——没有可见数字在动的时候空转一秒一次是白耗电。
 */
@Composable
fun rememberTickingNow(intervalMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (isActive) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/*
 * ── 实体图标 ────────────────────────────────────────────────────
 * 首页改之前整屏只有 7 个图标、21 处引用，取件点是什么类型、这一堆里有没有别人的件、
 * 急到什么程度——全靠文字承担。扫视时文字是最慢的通道，所以给这三类实体各配一个图形锚点。
 *
 * 图标是自绘的 24dp 描边矢量（res/drawable/ic_*.xml），**没有引 material-icons-extended**：
 * README 里「刻意少依赖，为了让贡献者能把项目跑起来」是有意写下的取舍，不为五个图标破例。
 *
 * 只给管理模式用。现场模式那一屏的契约是纯黑 on 纯白 + 三态，不引图标。
 */

/** 取件点类型的图形锚点。文字标签继续保留——颜色与图形之外还要有文字（人群适配策略）。 */
@DrawableRes
fun pointIconOf(kind: PointKind): Int = when (kind) {
    PointKind.LOCKER -> R.drawable.ic_point_locker
    PointKind.STATION -> R.drawable.ic_point_station
    PointKind.SELFPICK -> R.drawable.ic_point_selfpick
}

/**
 * 三态里只有「临期」档原先没有任何图形，而它恰恰是最需要被一眼认出的那一档。
 * 安全用已有的勾、过期风险用已有的告警三角，所以这里只导出中间这一档。
 */
@DrawableRes
val URGENCY_DUE_SOON_ICON: Int = R.drawable.ic_urgency_clock

/** 代取件是复数的人形，不是带勾的人；自己的件不需要图形锚点，所以只有这一个方向有图标。 */
@DrawableRes
val FAMILY_ICON: Int = R.drawable.ic_owner_family
