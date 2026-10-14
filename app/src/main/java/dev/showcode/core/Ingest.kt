package dev.showcode.core

import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.MessageDomain
import dev.showcode.core.parse.MessageDomains
import dev.showcode.core.parse.ParsedParcel
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.UnrecognizedReason
import java.security.MessageDigest
import java.text.Normalizer

/**
 * 入库裁决——整条链路上唯一决定「这条消息该不该动库」的地方。
 *
 * 为什么它是纯函数：批 1 把裁决写在 `ParcelRepository.upsert` 和 `recordUnrecognized` 里，
 * 而 Repository 是 Android 类（Context + ContentResolver），一行都测不到。于是
 * 「已取的被刷新复活」「忽略过的又回来」这两个 bug 能在仓库里活很久——不是没人看代码，
 * 是这一层从来没有测试（`core/` 有 19 个测试文件，`data/` 一个都没有）。
 * 判断挪到这里之后，Repository 只剩取数和执行。
 */

/** 一条消息的稳定身份。 */
fun messageFingerprint(channel: Source, sender: String, body: String): String {
    val material = "${channel.name}\u0000${sender.trim()}\u0000${normalizeBody(body)}"
    return sha256Hex(material)
}

/** 同一条正文在 unrecognized 表里的身份。不含发件人：同一句话由号码和 App 各报一次是常态。 */
fun bodyFingerprint(body: String): String = sha256Hex(normalizeBody(body))

/**
 * NFKC 折全角/半角，再把空白压成单空格。
 * 归一化只到这里为止——正文是要回显给人看的原文，任何改写都会让用户对不上他手机里那条短信。
 */
private fun normalizeBody(body: String): String =
    Normalizer.normalize(body, Normalizer.Form.NFKC).replace(WhitespaceRun, " ").trim()

private val WhitespaceRun = Regex("\\s+")

private fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    val out = StringBuilder(digest.size * 2)
    for (b in digest) {
        out.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
    }
    return out.toString()
}

private const val HEX = "0123456789abcdef"

/**
 * 库里已存在的、和这条消息撞码撞点的行。
 *
 * **任意 picked 状态都要带回来。** 只看未取行（旧版 `findUnpicked` 的 `AND picked = 0`）
 * 正是「已取的被刷新复活」的根因：已取行对去重不可见，于是重放的旧短信插出一条新卡。
 */
data class ExistingParcel(val id: Long, val picked: Boolean, val pickedAt: Long?, val receivedAt: Long)

/** 用户在这条未识别消息上的表态。 */
enum class UnrecognizedState { NEW, DISMISSED, RESOLVED }

/** 整条消息级的跳过原因。 */
enum class MessageSkip { NONE, ALREADY_SEEN, MUTED_SENDER }

/** 对一件解析成功的包裹，库里该怎么动。 */
sealed interface ParcelAction {
    val parsed: ParsedParcel
    val pointKey: String

    data class Insert(override val parsed: ParsedParcel, override val pointKey: String) : ParcelAction

    /** 同一件的另一路报道（短信 + 通知双报、驿站二次提醒）：更新已有行，绝不碰 picked。 */
    data class Update(val id: Long, override val parsed: ParsedParcel, override val pointKey: String) : ParcelAction

    /** 这条消息说的件已经被取走了：一个字都不动。 */
    data class AlreadyPicked(val id: Long, override val parsed: ParsedParcel, override val pointKey: String) : ParcelAction
}

/** 对一条没认出来的消息，库里该怎么动。 */
sealed interface UnrecognizedAction {
    data class Insert(
        val sender: String,
        val body: String,
        val bodyKey: String,
        val reason: UnrecognizedReason,
        val domain: MessageDomain,
        val receivedAt: Long,
    ) : UnrecognizedAction

    /** 库里已经有这条正文（在看、已忽略、或已手动提取过）：不再重复插。 */
    data object Suppressed : UnrecognizedAction
}

data class IngestPlan(
    val skip: MessageSkip,
    val parcels: List<ParcelAction>,
    val unrecognized: UnrecognizedAction?,
    val fingerprint: String,
) {
    /**
     * 只给「真的写进了 parcel 表」的消息记账。
     *
     * ⚠️ 未识别**不记账**，这是用户自定义解析规则的前提。记了账，下一次刷新会在解析之前
     * 就被 [MessageSkip.ALREADY_SEEN] 整条跳过（见 [planIngest] 第一行），于是用户后来
     * 补的那条规则永远看不到这条消息——失败列表成了一条死路。
     * 不记账也不会重复插行：未识别自己按 `body_fp` 去重（库里已有这条正文即 Suppressed），
     * 代价只是每次刷新多跑几趟正则。
     *
     * 反过来，一旦某条消息被记了账，它就再也拿不到第二次机会，所以这条判据要往保守的一侧站。
     */
    val worthLedgering: Boolean
        get() = parcels.any { it is ParcelAction.Insert || it is ParcelAction.Update }
}

/**
 * 同一条事件经短信与通知各报一次时，两个时间戳的允许偏差。
 * 超出这个偏差就不该再当成同一条消息——那是「码被复用」和「旧消息重放」的分界线。
 */
private const val SAME_MESSAGE_CLOCK_SKEW_MS = 5 * 60 * 1000L

/**
 * 一条消息进来，系统该做什么。全部判断在这，输入是已经取好的事实。
 *
 * 两道防线，缺一不可，因为它们各挡一类重复：
 *
 * 1. **[MessageSkip.ALREADY_SEEN]** —— 同一条消息被重放（每次刷新都会发生）。
 * 2. **[ParcelAction.AlreadyPicked]** —— 不同的消息报道同一件已取走的件。
 *    账本挡不住这一类：通知和短信指纹不同，驿站二次提醒更是全新的一条。
 *
 * 第 2 道判据故意往保守侧站：**拿不准就当成新件**。
 * 误插一张卡，用户点一下「已取」就纠正了；误吞一个真码，是他站在柜机前才发现。
 */
fun planIngest(
    message: IncomingMessage,
    outcome: ParseOutcome,
    fingerprint: String,
    alreadySeen: Boolean = false,
    senderMuted: Boolean = false,
    existingParcels: Map<Pair<String, String>, ExistingParcel> = emptyMap(),
    unrecognizedState: UnrecognizedState? = null,
    domain: MessageDomain = MessageDomains.classify(message.body),
): IngestPlan {
    // 消息级跳过先于解析结果：账本命中时连「它到底认没认出来」都不该再判断一次。
    if (alreadySeen) return IngestPlan(MessageSkip.ALREADY_SEEN, emptyList(), null, fingerprint)
    if (senderMuted) return IngestPlan(MessageSkip.MUTED_SENDER, emptyList(), null, fingerprint)
    return when (outcome) {
        ParseOutcome.Ignored -> IngestPlan(MessageSkip.NONE, emptyList(), null, fingerprint)
        is ParseOutcome.Parsed -> IngestPlan(
            skip = MessageSkip.NONE,
            parcels = outcome.parcels.map { parcelAction(it, message, existingParcels) },
            unrecognized = null,
            fingerprint = fingerprint,
        )
        is ParseOutcome.Rejected -> IngestPlan(
            skip = MessageSkip.NONE,
            parcels = emptyList(),
            unrecognized = if (unrecognizedState == null) {
                UnrecognizedAction.Insert(
                    sender = message.sender,
                    body = message.body,
                    bodyKey = bodyFingerprint(message.body),
                    reason = outcome.reason,
                    domain = domain,
                    receivedAt = message.receivedAt,
                )
            } else {
                UnrecognizedAction.Suppressed
            },
            fingerprint = fingerprint,
        )
    }
}

private fun parcelAction(
    parsed: ParsedParcel,
    message: IncomingMessage,
    existingParcels: Map<Pair<String, String>, ExistingParcel>,
): ParcelAction {
    val key = pointKey(parsed.pointName)
    val found = existingParcels[parsed.code to key]
    return when {
        found == null -> ParcelAction.Insert(parsed, key)
        // 未取件：同一件的另一路报道。更新它，让补上的柜号/归属生效。
        !found.picked -> ParcelAction.Update(found.id, parsed, key)
        // 已取件 + 这条消息比建行的那条更新 → 取件码被复用了，是一件新货。
        message.receivedAt > found.receivedAt + SAME_MESSAGE_CLOCK_SKEW_MS -> ParcelAction.Insert(parsed, key)
        else -> ParcelAction.AlreadyPicked(found.id, parsed, key)
    }
}

/** 发件人屏蔽键。号码归一化到数字，App 名（通知通道）折成小写。 */
fun muteKey(sender: String): String {
    val trimmed = sender.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (digits.isNotEmpty()) digits else trimmed.lowercase()
}
