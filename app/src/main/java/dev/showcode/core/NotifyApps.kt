package dev.showcode.core

import dev.showcode.core.parse.IncomingMessage

/**
 * 一个**推过通知**的 App。这张表存在的唯一理由是：用户想知道自己能关什么、已经关了什么。
 *
 * 它记的是「见过」而不是「装了什么」——只有真的推过通知的 App 才进得来。
 * 否则那一屏会变成一整台手机的 App 清单，而没人能在几十行里找到那个菜鸟。
 */
data class NotifyApp(
    /** 已归一化（小写、去空白）的包名。它是主键，也是唯一的判据。 */
    val packageName: String,
    /** 显示名。只用来给人看——判据一律走包名，理由见 [IncomingMessage.sourcePackage]。 */
    val label: String,
    val disabled: Boolean,
    val lastSeenAt: Long,
    /** 推过几条。只用来排序，不参与任何「要不要收」的判断。 */
    val seenCount: Int = 0,
    /**
     * 这个 App 转发过**别人的**取件码（妈妈经微信发来的那种）。
     *
     * 存成一个标记而不是当场去翻历史：通知正文不入库，事后无从查起，而这一格决定的是
     * 「关掉它之前要不要先警告」——那句话必须在他早就不推通知的那天也讲得出来。
     * 判据本身不在这里重做，写库时问 [ownerOfForward] 就行：两处对同一个 App
     * 必须给同一个答案。
     */
    val forwardsFamily: Boolean = false,
)

/**
 * 「按 App 关通知」的判据。
 *
 * 三条都不能省，每一条都对应一种已经想清楚的伤害：
 *
 * 1. **只管通知渠道。** 短信走发件人号码那一套管。两套搅在一起，「关掉菜鸟 App」
 *    就会连带让菜鸟的短信静默——那不是任何人的意图，而且症状查不出来。
 * 2. **认不出包名的通知照收。** 拿不到包名的通道是少数（系统级那一类），
 *    而误挡的代价是他站在柜机前才发现少了一件。
 * 3. **默认全开。** 这张表一条现有噪音都不会少，它只是把「能关」这个能力交出去。
 *    这一条要在开关那一屏明说，否则用户会以为打开这个功能就等于世界安静了。
 */
object NotifyApps {

    /** 归一化包名。库里存的、拿来比的都必须是这个形状，否则同一个 App 会变成两行。 */
    fun normalizePackage(packageName: String?): String? =
        packageName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** 这条消息该不该继续往库里走。[disabled] 是用户关掉的那些（已归一化的）包名。 */
    fun admits(message: IncomingMessage, disabled: Set<String>): Boolean {
        if (message.channel != Source.NOTIFICATION) return true
        val pkg = normalizePackage(message.sourcePackage) ?: return true
        return pkg !in disabled
    }

    /**
     * 开关列表的排序判据：推得最多的排前面，次键用最近时间。
     *
     * 「被挡掉的那些要不要记账」不在这里判——挡掉的消息一条 parcel 都不产生，
     * [IngestPlan.worthLedgering] 自然为 false。再写一遍就是留给下一个人的第二份真相，
     * 而那条真相将来会跟 F1 的「未识别不记账」打架。
     */
    fun sortedForSwitches(apps: List<NotifyApp>): List<NotifyApp> =
        apps.sortedWith(compareByDescending<NotifyApp> { it.seenCount }.thenByDescending { it.lastSeenAt })
}
