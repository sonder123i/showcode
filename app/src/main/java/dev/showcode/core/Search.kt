package dev.showcode.core

import java.text.Normalizer
import java.util.Calendar

/**
 * 首页检索的判据。
 *
 * 它只做一件事：**过滤，不重排**。组间顺序、组内顺序、取件点这个一级 IA 全部原样留着——
 * 搜「中通」的时候你看到的仍然是「哪个点有几件」，而不是一个按时间排的包裹列表。
 * 那一屏的契约是「5 秒内看清还得去哪几个点」，检索不能把它换成一张搜索结果列表。
 */
enum class SearchWindow {
    /** 不筛时间。 */
    ALL,

    /**
     * 「该现在去的那些」——临期**和**过期风险两档都要。
     *
     * 只筛 `DUE_SOON` 会把已经红着的那些挡在结果外：那一档显然更不是「可以晚点去」，
     * 而且用户看不出自己被挡了——屏上少了的是红卡，他会以为今天没有急件。
     */
    URGENT,

    /** 今天到的。按本地日历的零点算，和驿站「第二天早上自动回到安全」用同一套日界。 */
    TODAY,
}

/** 检索结果。[pickedMatches] 是空结果那句「这件你已经取过了」的唯一依据。 */
data class SearchResult(
    val groups: List<PickupPointGroup>,
    /** 查询命中了**已取走**的几件。0 表示这串字符在活件和已取里都不存在。 */
    val pickedMatches: Int,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val matchedParcels: Int get() = groups.sumOf { it.items.size }
}

/**
 * 在已经分好组的活件上做一次检索。
 *
 * @param groups 来自 [groupByPoint] 的分组（它已经滤掉已取走的）。
 * @param picked 已取走的那些。单独传进来只为了一件事：**空结果要说清是「没有」还是「你已经取过了」**。
 *   这两种情况对用户是完全不同的两句话，而「走到点上一件也对不上」是他今天最不想碰到的一次白跑。
 * @param query 原样输入的查询。空白查询等于不筛文本。
 */
fun searchParcels(
    groups: List<PickupPointGroup>,
    picked: List<Parcel>,
    query: String,
    window: SearchWindow = SearchWindow.ALL,
    now: Long,
    thresholds: UrgencyThresholds = UrgencyThresholds(),
): SearchResult {
    val needle = canonicalMatch(query)
    val todayStart = if (window == SearchWindow.TODAY) startOfToday(now) else Long.MIN_VALUE

    val filtered = groups.mapNotNull { group ->
        val items = group.items.filter {
            it.receivedAt >= todayStart &&
                inWindow(it, window, now, thresholds) &&
                (needle.isEmpty() || matchesCanonical(it, group.name, needle))
        }
        if (items.isEmpty()) null
        // 组内顺序沿用输入（已经是「紧急度 → 放得最久」），但 worst 必须跟着剩下那些重算：
        // 拿过滤前最急的那件给一张已经没有它的卡上色，报的是不存在的时间压力。
        else group.copy(items = items, worst = urgencyOf(items.first(), now, thresholds))
    }

    // 空白查询不报已取：否则一打开首页就看见「已经取过 30 件」，那是把统计塞进检索框。
    val pickedMatches = if (needle.isEmpty()) 0 else picked.count { matchesQuery(it, null, query) }
    return SearchResult(filtered, pickedMatches)
}

/** 单个件是否命中查询。给调用方（组件、列表）单独问一次用。 */
fun matchesQuery(parcel: Parcel, groupName: String?, query: String): Boolean =
    matchesCanonical(parcel, groupName.orEmpty(), canonicalMatch(query))

private fun inWindow(parcel: Parcel, window: SearchWindow, now: Long, thresholds: UrgencyThresholds): Boolean =
    window != SearchWindow.URGENT || urgencyOf(parcel, now, thresholds) != Urgency.SAFE

/**
 * 匹配哪些字段是**闭集**：码 / 点名 / 组显示名 / 承运商 / 柜号。
 *
 * ⚠️ 不含 `raw`。原文里躺着营业时间、运单号、发货城市，搜「18:00」或一串 12 位数字
 * 会把完全无关的件点回来——误命中的代价是他走到错的点，漏命中的代价只是再搜一次。
 */
private fun matchesCanonical(parcel: Parcel, groupName: String, needle: String): Boolean {
    if (needle.isEmpty()) return true
    val fields = listOf(parcel.code, parcel.pointName, groupName, parcel.carrier, parcel.lockerNo.orEmpty())
    // 码额外再用「去掉分隔符」的形状比一次：真人脑子里记的是「527712」这一串数字，
    // 而短信里的写法是「5-2-7712」。这一格不匹配，检索在最常用的那个输入方式上就是坏的。
    val bareCode = parcel.code.replace("-", "")
    val bareNeedle = needle.replace("-", "")
    return fields.any { canonicalMatch(it).contains(needle) } ||
        (bareNeedle.isNotEmpty() && bareCode.contains(bareNeedle))
}

/**
 * 匹配用的归一化：NFKC 折全角/半角、小写、空白折成单空格。
 *
 * 和 `bodyFingerprint` 那一层**同源但不同义**：那边要给同一条正文算身份，不许大小写折叠
 * （正文要回显给人看）；这边只是比子串，折叠掉的一切都是安全的。
 * 所以两处各自实现，而不是共用一个函数把两种语义焊死在一起。
 */
private fun canonicalMatch(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
        .lowercase()
        .replace(Whitespace, " ")

private val Whitespace = Regex("\\s+")

/** 本地日历的零点。用 Calendar 而不是 java.time，和 [closingInstant] 保持同一套日界。 */
private fun startOfToday(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
