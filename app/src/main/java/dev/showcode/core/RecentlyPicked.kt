package dev.showcode.core

/**
 * 「最近已取」那一栏的窗口。
 *
 * 它唯一的职责是**误触的补救出口**：3 秒撤销条一过，「已取」就成了一条没人替你反悔的物理事实
 * （它记的是「我把这件拿走了」，中途退出现场模式当然还在）。而误触被发现的时刻几乎都在同一次
 * 取件行程里——早上取完、上午晚些才发现少了一件，是这条栏存在的典型场景。
 *
 * 原来是 24 小时。那是一条机器边界：用户算不出自己还剩多久，而且它必然跨到第二天，
 * 于是首页最贵的位置长期挂着一坨历史。改成 6 小时——当天之内一定看得见，隔夜就干净了。
 */
const val RECENTLY_PICKED_WINDOW_MS = 6 * 60 * 60 * 1000L

/** 最多列几条。它是出口不是主路径，不该占满一屏。 */
const val RECENTLY_PICKED_LIMIT = 5

/** 窗口内、按取走时间倒序、最多 [limit] 条。没记到时间的「已取」不算——它没有可反悔的时点。 */
fun recentlyPicked(
    parcels: List<Parcel>,
    now: Long,
    limit: Int = RECENTLY_PICKED_LIMIT,
): List<Parcel> = parcels
    .filter { it.picked && (it.pickedAt ?: 0L) > now - RECENTLY_PICKED_WINDOW_MS }
    .sortedByDescending { it.pickedAt }
    .take(limit)
