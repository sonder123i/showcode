package dev.showcode.core.parse.corpus

import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.parse.UnrecognizedReason

/** 一条消息该被分成哪一档。和 ParseOutcome 一一对应，但用枚举好统计。 */
enum class Tier { PARSED, REJECTED, IGNORED }

/**
 * 一条语料的真值。
 *
 * 真值字段为 null 表示**这条不考核该字段**——很多真实短信根本没有足够的信息
 * 确定取件点名，硬写一个期望值只会把「我不知道」伪装成「它错了」。
 */
data class Expect(
    val tier: Tier,
    val code: String? = null,
    val lockerNo: String? = null,
    val pointName: String? = null,
    val kind: PointKind? = null,
    /** 相对 receivedAt 的免费保管截止（小时）。和 [closesAtMinute] 互斥。 */
    val freeUntilHours: Long? = null,
    /** 关门时刻（一天内分钟数）。 */
    val closesAtMinute: Int? = null,
    /**
     * 同一条短信里第二个及以后的取件码。
     * 现有 `ParseOutcome` 一次只返回一个件，所以这一项今天必然判漏——
     * 它不是苛刻，是把「一条两件时第二件永久消失」这个丢失量记进指标。
     */
    val extraCodes: List<String> = emptyList(),
    /** tier 为 REJECTED 时期望的原因。 */
    val reason: UnrecognizedReason? = null,
    /**
     * 点名里出现这些子串就判脏值。地址串冒充取件点名是真实高频错法，
     * 而「点名非空」这件事本身并不能说明它是对的。
     */
    val forbiddenInPointName: List<String> = emptyList(),
)

/**
 * 一条语料 = 一条原始消息 + 它的真值。
 *
 * ⚠️ 隐私红线（仓库是 public）：所有数字位值都做过轮换、所有小区/商铺/街道名都换成了假名，
 * 但**长度、字符类、连字符位置、后缀词（驿站/柜/超市/代收点/支局）保持不变**——
 * 解析器认的就是这些形态，形态在，语料才有意义。
 */
data class CorpusCase(
    val id: String,
    /** 出处，失败时直接读这一行就知道去哪找原文。 */
    val origin: String,
    val body: String,
    val sender: String = "1069000001",
    val channel: Source = Source.SMS,
    val expect: Expect,
    /** false = 真值本身有争议（例如码写在快递公司名后面），只报数、不进棘轮分母。 */
    val authoritative: Boolean = true,
)

val ALL: List<CorpusCase> = Upstream.CASES + Formats.CASES + Negatives.CASES
