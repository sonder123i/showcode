package dev.showcode.core

import dev.showcode.core.qr.Qr

/** 这一行是「一张要扫的码」，还是「一个去官方 App 取件的入口」。 */
enum class IdentityKind { CODE, ENTRY }

/**
 * 一张**身份码**：不是「某件快递的取件码」，而是「我是谁」——驿站自助机、柜机寄件、
 * 小区门禁那类要扫一下才开门的东西。
 *
 * 它刻意不并进 `Parcel` 的模型里。身份码没有取件点、没有时效、没有来源短信，
 * 硬塞进 `Parcel` 会让 `Grouping` / `Urgency` 的全部判据多一个恒为空的分支——
 * 而那些判据是这个 App 里被测得最狠的部分。所以它是独立的一张表、独立的一屏。
 *
 * ⚠️ 同一张表也放 [IdentityKind.ENTRY]——一个**不带码**的跳转入口。这不是顺手加的功能，
 * 是把主次摆正：竞品那一格（顶栏菜单里的「淘宝身份码」「拼多多身份码」）**本来就只有跳转**，
 * 维护者原话是「只是跳转。身份码是动态刷新的」。而我们原来把「存一张码」做成主体、
 * 跳转做成附属——对那类每几十秒换一次的码，那是默认展示一张废码。
 */
data class IdentityCode(
    val id: Long = 0,
    /** 现场要念给人看的那一行：「菜鸟驿站会员码」「丰巢寄件码」。必填，因为它就是这张卡的标题。 */
    val label: String,
    /** 码值本体，也就是二维码的内容。ENTRY 那一类没有它。 */
    val value: String,
    val kind: IdentityKind = IdentityKind.CODE,
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
    /** 官方 App 的包名。有它才有「跳官方 App」那条路；ENTRY 必须有它。 */
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
     * 两条分支的必填不一样，因为**它们坏起来的方式不一样**：
     * - `CODE` 缺码值就没有码可画；码值编不进二维码也算缺（长度上限见下面那条注释）。
     * - `ENTRY` 缺包名就什么都做不了——它是一扇门，不是一张纸。
     * 而 `ENTRY` **不问「会不会变」**：它没有会作废的内容，问那一格是拿判据烦人。
     *
     * ⚠️ 码值的长度上限**不是这里写死的一个数字**，而是「真的编一遍码」的结果
     * （[dev.showcode.core.qr.MAX_VERSION]）。拍一个「300 字符」之类的上限会同时犯两个错：
     * 短到编不进 15 版的那部分被放过（柜机前扫不出来），长得了的那部分被误拒。
     *
     * `dynamic == null` 排在 CODE 的第一个：这一格**不给默认值**。猜成静态，他举着一张
     * 已经作废的码站在机器前毫不知情；猜成动态，屏上多一句提醒，最多让他烦一次。
     */
    fun validate(
        label: String,
        value: String,
        kind: IdentityKind,
        dynamic: Boolean?,
        appPackage: String?,
        existingCount: Int,
    ): String? {
        val trimmedLabel = label.trim()
        val trimmedValue = value.trim()
        if (trimmedLabel.isEmpty()) return IdentityReject.EMPTY_LABEL
        if (trimmedLabel.length > MAX_LABEL_LENGTH) return IdentityReject.LABEL_TOO_LONG
        if (existingCount >= MAX_CODES) return IdentityReject.TOO_MANY
        return when (kind) {
            IdentityKind.ENTRY ->
                if (appPackage.isNullOrBlank()) IdentityReject.NEEDS_PACKAGE else null

            IdentityKind.CODE -> {
                if (dynamic == null) return IdentityReject.NEEDS_DYNAMIC
                if (trimmedValue.isEmpty()) return IdentityReject.EMPTY_VALUE
                if (Qr.of(trimmedValue) == null) return IdentityReject.VALUE_NOT_ENCODABLE
                null
            }
        }
    }

    /**
     * 亮码那一屏该不该说不吉利的那句话。
     *
     * 判据只有一条：标了「会变」。至于它**这会儿**到底变了没有，离线状态下一无所知，
     * 所以这一屏不许假装知道——它只能说「这种码会变，别的信得过这张屏」。
     */
    fun shouldWarnStale(code: IdentityCode): Boolean = code.dynamic

    /**
     * 出示那一屏**默认**画不画这张二维码。
     *
     * 会话级动态码画满一屏，是在给用户一张「看起来完整、其实已经作废」的码——
     * 而这一屏的失效方式从来不是报错，是他举着这张屏扫了三次才发现白扫。
     *
     * ⚠️ 折叠的正当性**只来自有一条更好的退路**（选过官方 App）。没有退路还把码藏起来，
     * 等于把唯一剩下的信息也拿走，那是替用户把这件事做成死路——所以这里必须有 `appPackage`。
     * 他点过「还是出示这一份旧的」之后也不再折叠：那是他自己的决定，不是我们的默认。
     */
    fun shouldCollapseQr(code: IdentityCode, revealed: Boolean): Boolean =
        code.dynamic && code.appPackage != null && !revealed

    /**
     * 出示那一屏的亮度。**抬到不低于 [BRIGHTNESS_FLOOR]，但用户自己调得更高的不降下来。**
     *
     * 不是拉满 1.0：柜机那颗摄像头要的是**对比**不是最大亮度，而夜里在楼下取件时
     * 1.0 是拿屏幕照人眼。null（还没读过）和负值（跟随系统的 -1f）都按地板给。
     */
    fun liftedBrightness(previous: Float?): Float =
        if (previous == null || previous < BRIGHTNESS_FLOOR) BRIGHTNESS_FLOOR else previous

    const val BRIGHTNESS_FLOOR = 0.7f
}

/** 拒收理由。和解析规则同一套做法：稳定的键，文案在 UI 层。 */
object IdentityReject {
    const val NEEDS_DYNAMIC = "needs_dynamic"
    const val NEEDS_PACKAGE = "needs_package"
    const val EMPTY_LABEL = "empty_label"
    const val LABEL_TOO_LONG = "label_too_long"
    const val EMPTY_VALUE = "empty_value"
    const val VALUE_NOT_ENCODABLE = "value_not_encodable"
    const val TOO_MANY = "too_many"
}
