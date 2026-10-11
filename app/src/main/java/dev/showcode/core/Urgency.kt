package dev.showcode.core

import java.util.Calendar

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS

/**
 * 临期阈值。frame.json → urgency_thresholds 已锁定「给默认值 + 允许用户调」，
 * 且设置页只需要 2 个滑块：「过期风险」不可调，因为它改变的是「计划」而不是「紧迫度」。
 *
 * 默认值来源（同样是 frame 锁定的）：
 * - 丰巢柜 剩余免费 ≤6 小时 = 18 小时免费的 1/3，覆盖「下班后到睡前」这个唯一取件窗口
 * - 驿站 关门前 ≤90 分钟 = 通勤 30-45 分钟 + 缓冲；白跑一趟的代价远高于 3 元超时费，
 *   所以驿站这一档比丰巢激进
 */
data class UrgencyThresholds(
    val lockerDueSoonMs: Long = 6 * HOUR_MS,
    val stationDueSoonMinutes: Int = 90,
) {
    companion object {
        val LOCKER_OPTIONS_HOURS = listOf(2, 4, 6, 8)
        val STATION_OPTIONS_MINUTES = listOf(30, 60, 90, 120)
    }
}

/**
 * 快递柜免费保管时长（小时）。已核实规则：18 小时免费，之后 0.5 元/12 小时，上限 3 元。
 * 短信里显式写了「N 小时内免费」时以短信为准，见 core.parse。
 */
const val LOCKER_FREE_HOURS_DEFAULT = 18

/**
 * 驿站/自提点关门时刻的内置默认值（一天内分钟数）。
 *
 * ⚠️ 这是占位默认，不是数据：营业时间库是 V2 的众包数据资产，落地前只能给一个
 * 保守猜测。短信里显式写了营业时间或「X 点前取件」时以短信为准（见 core.parse），
 * 用户后续也能改。宁可默认值偏早（提前提醒），也不要偏晚（白跑一趟）。
 */
val DEFAULT_CLOSING_MINUTE = mapOf(
    PointKind.STATION to 21 * 60,
    PointKind.SELFPICK to 22 * 60,
)

/** 该件的截止时刻（epoch millis）。没有依据时返回 null——不编造时效。 */
fun deadlineOf(parcel: Parcel, now: Long): Long? = when (parcel.pointKind) {
    PointKind.LOCKER -> parcel.freeUntilAt
    PointKind.STATION, PointKind.SELFPICK -> closingInstant(parcel.closesAtMinuteOfDay, now)
}

/**
 * 今天的关门时刻。已经过了就返回过去的那个时刻，于是 urgencyOf 判为「已关门」；
 * 第二天早上它又变成未来的时刻，状态自动回到安全——驿站确实会再开门。
 */
fun closingInstant(minuteOfDay: Int?, now: Long): Long? {
    if (minuteOfDay == null) return null
    return Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun minuteOfDayOf(now: Long): Int = Calendar.getInstance().apply { timeInMillis = now }
    .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

/** 三态判定。null 截止时刻一律算安全：没有依据就不制造焦虑。 */
fun urgencyOf(parcel: Parcel, now: Long, thresholds: UrgencyThresholds = UrgencyThresholds()): Urgency {
    val deadline = deadlineOf(parcel, now) ?: return Urgency.SAFE
    val remaining = deadline - now
    if (remaining <= 0) return Urgency.AT_RISK
    val dueSoonWindow = when (parcel.pointKind) {
        PointKind.LOCKER -> thresholds.lockerDueSoonMs
        PointKind.STATION, PointKind.SELFPICK -> thresholds.stationDueSoonMinutes * MINUTE_MS
    }
    return if (remaining <= dueSoonWindow) Urgency.DUE_SOON else Urgency.SAFE
}

/**
 * 临期态的倒计时。三态下这是唯一的紧迫度梯度载体，必须随时间连续变化
 * （「剩 4小时12分」→「剩 1小时52分」→「剩 38分07秒」），不加第四个色阶。
 */
data class Countdown(val hours: Int, val minutes: Int, val seconds: Int) {
    /** 与 mockup 的 useTickingMinutes 一致：有小时就只显示到分，没小时才显示到秒。 */
    val usesHours: Boolean get() = hours > 0
}

fun countdownOf(remainingMs: Long): Countdown {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    return Countdown(
        hours = (totalSeconds / 3600).toInt(),
        minutes = ((totalSeconds % 3600) / 60).toInt(),
        seconds = (totalSeconds % 60).toInt(),
    )
}

/** 已存放天数。>1 时现场模式提示「可能已失效」，但绝不阻断出示。 */
fun staleDaysOf(parcel: Parcel, now: Long): Int = ((now - parcel.receivedAt) / DAY_MS).toInt()
