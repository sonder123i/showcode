package dev.showcode.core

import dev.showcode.core.qr.Qr

/**
 * 一张**身份码**：不是「某件快递的取件码」，而是「我是谁」——驿站自助机、柜机寄件、
 * 小区门禁那类要扫一下才开门的东西。
 *
 * 它刻意不并进 `Parcel` 的模型里。身份码没有取件点、没有时效、没有来源短信，
 * 硬塞进 `Parcel` 会让 `Grouping` / `Urgency` 的全部判据多一个恒为空的分支——
 * 而那些判据是这个 App 里被测得最狠的部分。所以它是独立的一张表、独立的一屏。
 */
data class IdentityCode(
    val id: Long = 0,
    /** 现场要念给人看的那一行：「菜鸟驿站会员码」「丰巢寄件码」。必填，因为它就是这张卡的标题。 */
    val label: String,
    /** 码值本体，也就是二维码的内容。 */
    val value: String,
    /**
     * 这张码**会不会变**。会话级动态码（在官方 App 里每 60 秒换一次的那种）为 true。
     *
     * 单独立一个字段是因为代价不对称：静态码存下来是资产，动态码存下来是一颗定时炸弹——
     * 用户会在柜机前举着一张已经失效的码，而且屏上看起来和有效的一模一样。
     * 这一格就是为了让那一屏能说那句「这张可能已经失效，直接开官方 App 更稳」。
     */
    val dynamic: Boolean = false,
    /** 哪来的、怎么用，随便写。没有它，两周后没人记得这张码是给谁的。 */
    val note: String? = null,
    /** 官方 App 的包名。有它才有「跳官方 App」那条降级路；不填就只出示码。 */
    val appPackage: String? = null,
    val createdAt: Long = 0L,
)

object IdentityCodes {

    const val MAX_LABEL_LENGTH = 20
    const val MAX_NOTE_LENGTH = 60
    const val MAX_CODES = 10

    /**
     * 校验。返回 null 表示可用，否则是 [IdentityReject] 里那把键。
     *
     * ⚠️ 码值的长度上限**不是这里写死的一个数字**，而是「真的编一遍码」的结果
     * （[dev.showcode.core.qr.MAX_VERSION]）。拍一个「300 字符」之类的上限会同时犯两个错：
     * 短到编不进 15 版的那部分被放过（柜机前扫不出来），长得了的那部分被误拒。
     *
     * `dynamic == null` 排在第一个：这一格**不给默认值**。这张码会不会变是只有用户知道的
     * 事实，而猜错的代价不对称——猜成静态，他举着一张已经作废的码站在机器前毫不知情；
     * 猜成动态，屏上多一句提醒，最多让他烦一次。
     */
    fun validate(label: String, value: String, dynamic: Boolean?, existingCount: Int): String? {
        if (dynamic == null) return IdentityReject.NEEDS_DYNAMIC
        val trimmedLabel = label.trim()
        val trimmedValue = value.trim()
        if (trimmedLabel.isEmpty()) return IdentityReject.EMPTY_LABEL
        if (trimmedLabel.length > MAX_LABEL_LENGTH) return IdentityReject.LABEL_TOO_LONG
        if (trimmedValue.isEmpty()) return IdentityReject.EMPTY_VALUE
        if (Qr.of(trimmedValue) == null) return IdentityReject.VALUE_NOT_ENCODABLE
        if (existingCount >= MAX_CODES) return IdentityReject.TOO_MANY
        return null
    }

    /**
     * 亮码那一屏该不该说不吉利的那句话。
     *
     * 判据只有一条：标了「会变」。至于它**这会儿**到底变了没有，离线状态下一无所知，
     * 所以这一屏不许假装知道——它只能说「这种码会变，别的信得过这张屏」。
     */
    fun shouldWarnStale(code: IdentityCode): Boolean = code.dynamic
}

/** 拒收理由。和解析规则同一套做法：稳定的键，文案在 UI 层。 */
object IdentityReject {
    const val NEEDS_DYNAMIC = "needs_dynamic"
    const val EMPTY_LABEL = "empty_label"
    const val LABEL_TOO_LONG = "label_too_long"
    const val EMPTY_VALUE = "empty_value"
    const val VALUE_NOT_ENCODABLE = "value_not_encodable"
    const val TOO_MANY = "too_many"
}
