package dev.showcode.core

/**
 * 首页的一级 IA：按取件点分组，组与组内都按三态紧急度排序。
 *
 * Story 2 AC2：第一级是取件点卡片，不是按时间排列的包裹列表。
 * Story 2 AC3：最急的在最上面，而不是最新到达的在最上面。
 */
fun groupByPoint(
    parcels: List<Parcel>,
    now: Long,
    thresholds: UrgencyThresholds = UrgencyThresholds(),
): List<PickupPointGroup> = parcels
    .filterNot { it.picked }
    .groupBy { it.pointKey }
    .map { (key, items) ->
        val sorted = items.sortedWith(parcelOrder(now, thresholds))
        // 显示名取最近一次入库的写法：同一个点在短信里可能有多种拼法，
        // 保守归一化不会合并它们，但同一个 pointKey 内部要用最新的那个名字。
        val freshest = items.maxBy { it.receivedAt }
        PickupPointGroup(
            key = key,
            name = freshest.pointName,
            kind = freshest.pointKind,
            closesAtMinuteOfDay = freshest.closesAtMinuteOfDay,
            items = sorted,
            worst = urgencyOf(sorted.first(), now, thresholds),
        )
    }
    .sortedWith(pointOrder())

/** 同档里放得最久的排前面——它离失效更近。 */
fun parcelOrder(now: Long, thresholds: UrgencyThresholds = UrgencyThresholds()): Comparator<Parcel> =
    compareBy<Parcel> { urgencyOf(it, now, thresholds).order }.thenBy { it.receivedAt }

/** 紧急度优先，件数只作次级排序（与 mockup Screen6 一致）。 */
fun pointOrder(): Comparator<PickupPointGroup> =
    compareBy<PickupPointGroup> { it.worst.order }.thenByDescending { it.items.size }
