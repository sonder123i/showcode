package dev.showcode.core.parse

import dev.showcode.core.DEFAULT_CLOSING_MINUTE
import dev.showcode.core.PointKind
import dev.showcode.core.Source

/**
 * 用户自己写的解析规则。
 *
 * 它只有一件事可做：**在内置判据一个件都没认出来的时候，补一个件出来**。
 * 这条「只做加法」的约束是整个功能的安全边界——用户规则不可能把原本认得的件弄丢，
 * 所以它不需要（也不允许）参与进门率、误收率那些棘轮指标的计分。
 *
 * 为什么需要它：语料里已经坐实有一类写法是内置闭集治不了的（品牌冒充门店、
 * 只存在于某个小区的叫法）。内置解析器不能再靠枚举救，但**住在某个小区的人知道自己家驿站叫什么**——
 * 把这一格知识交出去，比在第 43 条语料上再加一条正则诚实。
 */
data class ParseRule(
    val id: Long = 0,
    /** 命中取件码的正则。有捕获组取第 1 组，没有就取整段命中。 */
    val pattern: String,
    /** 这条规则认定的取件点名称。**必填**：写规则的人已经确认了是哪个点，所以它免掉确认这道门。 */
    val pointName: String,
    val lockerNo: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
)

object ParseRules {

    /** 正则长度上限。超 200 字符的正则基本是「我在编一个解析器」而不是「我在补一个写法」。 */
    const val MAX_PATTERN_LENGTH = 200

    /** 点名长度上限，和内置点名同宽。 */
    const val MAX_POINT_NAME_LENGTH = 40

    /** 规则条数上限。这一层没有任何自动学习，几十条人就管不动了，上限要给得诚实。 */
    const val MAX_RULES = 20

    /**
     * 校验一条规则。返回 null 表示可用，否则是人能看懂的拒收理由。
     *
     * 编译性必须在录入时就验，不能留到解析时——[apply] 那边是 `runCatching` 静默失效的，
     * 一条永远命中不了的规则留在列表里，用户会以为它在工作。
     */
    fun validate(pattern: String, pointName: String, existingCount: Int): String? {
        val trimmedPattern = pattern.trim()
        val trimmedName = pointName.trim()
        if (trimmedPattern.isEmpty()) return RuleReject.EMPTY_PATTERN
        if (trimmedPattern.length > MAX_PATTERN_LENGTH) return RuleReject.PATTERN_TOO_LONG
        if (trimmedName.isEmpty()) return RuleReject.EMPTY_POINT_NAME
        if (trimmedName.length > MAX_POINT_NAME_LENGTH) return RuleReject.POINT_NAME_TOO_LONG
        if (existingCount >= MAX_RULES) return RuleReject.TOO_MANY_RULES
        // 只验能否编译，不验它像不像取件——用户写的东西不该被猜。
        runCatching { Regex(trimmedPattern) }.onFailure { return RuleReject.BAD_SYNTAX }
        return null
    }

    /**
     * 用规则补抽一个件。[alreadyKnown] 里的码是内置已经认出来的，一律不再重复。
     *
     * 命中但形状不对（太长/太短/是手机号）时**接着试下一条规则**而不是整条落空：
     * 「这条正则抓宽了」是可以由下一条规则纠正的事，不该把用户已经写对的别的规则一起废掉。
     */
    fun apply(
        rules: List<ParseRule>,
        body: String,
        receivedAt: Long,
        alreadyKnown: Set<String> = emptySet(),
    ): ParsedParcel? {
        for (rule in rules) {
            if (!rule.enabled) continue
            val code = matchCode(rule.pattern, body)?.takeIf { isCodeShape(it) }
                ?.takeUnless { it in alreadyKnown } ?: continue
            val kind = kindOfName(rule.pointName.trim())
            return ParsedParcel(
                code = code,
                lockerNo = rule.lockerNo?.takeIf { it.isNotBlank() },
                // 规则不带承运商信息，退成「快递」而不是替用户猜一家。
                carrier = "快递",
                pointName = rule.pointName.trim(),
                // 规则里没有「是哪个点」以外的信息，类型仍按点名判——点名是用户写的，可信。
                pointKind = kind,
                closesAtMinuteOfDay = DEFAULT_CLOSING_MINUTE[kind],
                freeUntilAt = null,
            )
        }
        return null
    }

    /**
     * 这条正则在这段文本上命中了什么串。有捕获组取第 1 组，没有就取整段命中。
     *
     * 这里**不做形状判断**：命中什么就是什么，能不能当一个件由 [apply] 决定。
     * 分两层是因为这两件事会被分别测试——「正则写错了」和「认出的串不像个码」
     * 是两种完全不同的失效，混在一起时 UI 没法告诉用户是哪一种。
     *
     * `internal` 而不是 private：规则最坏的失效方式是**看起来在工作、其实永远命中不了**，
     * 所以这一步要能被单独问。
     */
    internal fun matchCode(pattern: String, body: String): String? {
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return null
        val match = regex.find(body) ?: return null
        return match.groupValues.getOrElse(1) { match.value }.ifBlank { match.value }.trim().takeIf { it.isNotEmpty() }
    }

    private val PHONE_NUMBER = Regex("1\\d{10}")

    /** 和内置的 `kindOf` 同一套词表，但只看名字：规则不带正文语境。 */
    private val KIND_STATION = Regex("驿站")
    private val KIND_LOCKER = Regex("丰巢|智能柜|快递柜|蜂巢|菜鸟柜|速递易|保管箱|柜")
    private val KIND_SELFPICK = Regex("自提|便民|便利店|超市|门店")

    private fun kindOfName(name: String): PointKind = when {
        KIND_STATION.containsMatchIn(name) -> PointKind.STATION
        KIND_LOCKER.containsMatchIn(name) -> PointKind.LOCKER
        KIND_SELFPICK.containsMatchIn(name) -> PointKind.SELFPICK
        else -> PointKind.STATION
    }

    /**
     * 从一段原文里生成一条规则。
     *
     * 这是「不要求用户会写正则」的那一端：选中正文里的码串，把它左边那段锚定词
     * （「凭暗号」「取件密码」…）连上码本身生成一条规则，保存前当场用这条原文验一遍。
     *
     * 两处刻意的设计，都是实测出来的代价：
     *
     * 1. **码必须进捕获组。** 不带组时 [matchCode] 返回整段命中，那段锚定词会一起
     *    变成「码」——而这一串是人要念给柜机键盘听的。
     * 2. **上下文只取码的左侧，不取右侧。** 第一版两侧都取，等于把整句抄进正则；
     *    而码右边恰恰是变化最多的地方（柜号、货架号、「请及时取件」的措辞），
     *    带右边生成的规则换个说法就永不命中，留在列表里看起来完全是在工作的——
     *    这是本功能最坏的失效。左边的锚定词才是稳定成分，它正是内置抽码靠的东西。
     */
    fun literalRule(from: String, code: String, pointName: String): ParseRule? {
        if (code.isBlank() || from.isBlank()) return null
        val idx = from.indexOf(code)
        if (idx < 0) return null
        val anchor = contextOf(from.substring(0, idx), reverse = true)
        // 码顶在句首、左边只剩标点时，那一段就没什么可锚的，只能退而用右边。
        val tail = if (anchor.isEmpty()) contextOf(from.substring(idx + code.length), reverse = false) else ""
        if (anchor.isEmpty() && tail.isEmpty()) return null
        val pattern = buildString {
            if (anchor.isNotEmpty()) append(Regex.escape(anchor)).append("\\s*")
            append('(').append(Regex.escape(code)).append(')')
            if (tail.isNotEmpty()) append("\\s*").append(Regex.escape(tail))
        }
        if (pattern.length > MAX_PATTERN_LENGTH) return null
        return ParseRule(pattern = pattern, pointName = pointName.trim())
    }

    /**
     * 从码的一边往回取最多 [MAX_CONTEXT] 个字符，遇到断句符就停。
     * [reverse] = true 时从这段文本的**尾端**往回收（那是码的左侧）。
     */
    private fun contextOf(slice: String, reverse: Boolean): String {
        if (slice.isEmpty()) return ""
        val cut = if (reverse) {
            val stop = slice.indexOfLast { it in PUNCT }
            val from = if (stop < 0) 0 else stop + 1
            slice.substring(from).takeLast(MAX_CONTEXT)
        } else {
            val stop = slice.indexOfFirst { it in PUNCT }
            val to = if (stop < 0) slice.length else stop
            slice.substring(0, to).take(MAX_CONTEXT)
        }
        return cut.trim()
    }

    /** 单侧最多带这么多字符的上下文。再多就变成「整条短信」，那不是规则是硬编码。 */
    private const val MAX_CONTEXT = 6

    /** 中文短信里的断句符：锚定词到这里为止，不跨句。 */
    private val PUNCT = charArrayOf('，', ',', '。', '；', ';', '！', '？', ' ', '\n', '、', '：', ':')

    /**
     * 码的形状判据，和内置**同一条**：内置每条抽码正则的捕获组都是
     * `[0-9A-Za-z][0-9A-Za-z\-]{1,14}` 再裁到 2..12、并且不是手机号。
     *
     * 规则这里不能更宽。用户完全可能写一条**没有捕获组**的正则（`暗号[0-9]{4}`），
     * 那时命中串是整段「暗号8877」——内置永远不可能产出这种码，规则也不该产出：
     * 它会带着锚定词住进卡面，而念给柜机键盘听的正是那一串。
     */
    private val CODE_SHAPE = Regex("[0-9A-Za-z][0-9A-Za-z\\-]{1,11}")

    private fun isCodeShape(code: String): Boolean =
        CODE_SHAPE.matches(code) && !PHONE_NUMBER.matches(code)

    /**
     * 一条已存在的未识别正文，加上用户刚写的规则之后能不能变成件。
     *
     * 判据是「真的过一遍完整解析」，不是「规则命中就行」——规则和内置判据的相互作用
     * 只有走同一个 [PickupCodeParser.parse] 才算数。这条判据是纯的，所以这一整步可测。
     */
    fun reprocessTargets(messages: List<UnrecognizedRecord>, rules: List<ParseRule>): List<UnrecognizedRecord> {
        if (rules.none { it.enabled }) return emptyList()
        return messages.filter { record ->
            val outcome = PickupCodeParser.parse(
                IncomingMessage(
                    sender = record.sender,
                    body = record.body,
                    receivedAt = record.receivedAt,
                    channel = Source.SMS,
                ),
                rules,
            )
            outcome is ParseOutcome.Parsed
        }
    }
}

/** 重判要用的最小事实：库里那条未识别的正文。判断在 core，所以这里不带 Android 类型。 */
data class UnrecognizedRecord(val id: Long, val sender: String, val body: String, val receivedAt: Long)

/** 拒收理由。用稳定的键而不是文案，UI 层负责翻成人话。 */
object RuleReject {
    const val EMPTY_PATTERN = "empty_pattern"
    const val PATTERN_TOO_LONG = "pattern_too_long"
    const val EMPTY_POINT_NAME = "empty_point_name"
    const val POINT_NAME_TOO_LONG = "point_name_too_long"
    const val TOO_MANY_RULES = "too_many_rules"
    const val BAD_SYNTAX = "bad_syntax"
}
