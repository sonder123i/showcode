package dev.showcode.core

/**
 * 下拉栏磁贴该做什么、该说什么。
 *
 * 立这一层是因为磁贴是**整个 App 里最容易骗人的一块表面**：它只有一行标题一行副标题，
 * 而它显示的东西不需要任何人打开 App 就会被读到。所以「这时候该不该报数」
 * 必须是一条写下来的判据，而不是渲染时顺手写下的 `if`。
 */
enum class TileAction {
    /** 通知使用权没给：磁贴的唯一职责是把人送到能修它的地方。 */
    OPEN_LISTENER_SETTINGS,

    /** 权限在：磁贴做一件真事——立刻重读收件箱，而不是再开一次 App。 */
    REFRESH_NOW,
}

data class TileReadout(
    val action: TileAction,
    /**
     * 副标题准不准报件数。
     *
     * `false` 时磁贴只说「未开启」。**权限没给的时候那些数是真的不可信**——
     * 收件箱根本没在读，「没有待取件」是一块骗人的平安牌，
     * 而一个人看到平安牌就不会去查手机里那条短信。
     */
    val reportCounts: Boolean,
    val pending: Int,
    val urgent: Int,
)

/**
 * 磁贴的一次读数。
 *
 * 三条判据，每条都对应一种会白跑一趟的情形：
 *
 * 1. 权限没给 → 不报数，只把人送去设置。这条也决定了点击行为，所以标题和动作不会各说一套。
 * 2. 件数为 0 也**照常报「没有待取件」**，不把磁贴标成灰色。灰掉的磁贴读起来像坏了，
 *    而它此刻说的是实话——和「0 条也要有入口」那几格同一条理由。
 * 3. `urgent` 含临期与过期风险两档：磁贴上没有颜色，只有字，所以「急」必须把已经红的也算进去，
 *    否则最该被看见的那些反而在唯一的提醒位上消失。
 */
fun tileReadout(notificationGranted: Boolean, pending: Int, urgent: Int): TileReadout =
    if (!notificationGranted) {
        TileReadout(TileAction.OPEN_LISTENER_SETTINGS, reportCounts = false, pending = 0, urgent = 0)
    } else {
        TileReadout(TileAction.REFRESH_NOW, reportCounts = true, pending = pending, urgent = urgent)
    }

/** 待取件里算「急」的那些：临期 + 过期风险。安全的一档不算。 */
fun urgentCount(parcels: List<Parcel>, now: Long, thresholds: UrgencyThresholds = UrgencyThresholds()): Int =
    parcels.count { !it.picked && urgencyOf(it, now, thresholds) != Urgency.SAFE }
