package dev.showcode.core.parse

import dev.showcode.core.HOUR_MS
import dev.showcode.core.parse.corpus.CorpusCase
import dev.showcode.core.parse.corpus.Expect
import dev.showcode.core.parse.corpus.Tier
import kotlin.math.floor

/**
 * 解析质量的四个指标。它们各自管一件不同的事，合成一个「准确率」就没有意义了：
 *
 * - **进门率**：该收的收进来了吗（应 PARSED 且实际 PARSED）
 * - **字段准确率**：收进来之后抽得对不对（码 / 柜号 / 点名 / 类型 / 时限 各算一票）
 * - **无痕蒸发率**：漏掉的那些里，有多少连「未识别」都没进——**这是唯一用户永远不会知道的失败**
 * - **误收率**：不该进来的东西进来了多少（变成一件假快递，或污染未识别角标）
 */
object ParseMetrics {

    data class Report(
        val gateRecall: Double,
        val fieldAccuracy: Double,
        val unconfirmedRate: Double,
        val silentLoss: Double,
        val falseIntake: Double,
        val text: String,
    )

    private data class Outcome(
        val tier: Tier,
        val parcels: List<ParsedParcel>,
        val reason: UnrecognizedReason?,
        val codes: List<String>,
    )

    fun measure(cases: List<CorpusCase>, receivedAt: Long): Report {
        val scored = cases.filter { it.authoritative }
        val advisory = cases.filterNot { it.authoritative }

        // 全量都判一遍：存疑语料也要出现在报告里，只是不进分母。
        val outcomes = cases.associateWith { judge(it, receivedAt) }

        val shouldParse = scored.filter { it.expect.tier == Tier.PARSED }
        val didParse = shouldParse.filter { outcomes.getValue(it).tier == Tier.PARSED }
        val silentlyLost = shouldParse.filter { outcomes.getValue(it).tier == Tier.IGNORED }

        val units = buildList {
            didParse.forEach { case -> addAll(scoreFields(case, outcomes.getValue(case).parcels, receivedAt)) }
            addAll(refusalCarryUnits(scored, outcomes))
        }
        val hits = units.count { it.hit }

        // 认出了码的消息里，有多少条我们不敢替它决定「在哪个点」。
        val carriedCode = scored.filter { outcomes.getValue(it).codes.isNotEmpty() }
        val needConfirm = carriedCode.filter { outcomes.getValue(it).tier == Tier.REJECTED }

        val shouldStayOut = scored.filter { it.expect.tier != Tier.PARSED }
        val becameParcel = shouldStayOut.filter { outcomes.getValue(it).tier == Tier.PARSED }
        val pollutedUnrecognized = scored.filter {
            it.expect.tier == Tier.IGNORED && outcomes.getValue(it).tier == Tier.REJECTED
        }

        val gateRecall = rate(didParse.size, shouldParse.size)
        val fieldAccuracy = rate(hits, units.size)
        val silentLoss = rate(silentlyLost.size, shouldParse.size)
        val falseIntake = rate(becameParcel.size + pollutedUnrecognized.size, shouldStayOut.size)

        val unconfirmedRate = rate(needConfirm.size, carriedCode.size)

        return Report(
            gateRecall = gateRecall,
            fieldAccuracy = fieldAccuracy,
            unconfirmedRate = unconfirmedRate,
            silentLoss = silentLoss,
            falseIntake = falseIntake,
            text = buildString {
                appendLine("解析质量报告 · 语料 ${cases.size} 条（计分 ${scored.size}，存疑 ${advisory.size}）")
                appendLine()
                appendLine("进门率        ${pct(gateRecall)}   ${didParse.size}/${shouldParse.size}")
                appendLine("字段准确率    ${pct(fieldAccuracy)}   $hits/${units.size}")
                appendLine("需确认率      ${pct(unconfirmedRate)}   ${needConfirm.size}/${carriedCode.size}  ← 认出了码，但不敢替它定「在哪个点」")
                appendLine("无痕蒸发率    ${pct(silentLoss)}   ${silentlyLost.size}/${shouldParse.size}  ← 用户永远不会知道少了件")
                appendLine("误收率        ${pct(falseIntake)}   ${becameParcel.size} 条假快递 + ${pollutedUnrecognized.size} 条角标噪音 / ${shouldStayOut.size} 条负样本")
                appendLine()
                appendLine("── 漏项清单（按「用户看不看得见」排序）──")
                silentlyLost.forEach { appendLine("  🔴 无痕 ${it.id} ${it.origin}") }
                shouldParse.filter { outcomes.getValue(it).tier == Tier.REJECTED }.forEach {
                    appendLine("  🟡 落未识别 ${it.id} ${it.origin} → 实际原因 ${outcomes.getValue(it).reason}")
                }
                needConfirm.forEach { appendLine("  🟠 待确认 ${it.id} ${it.origin} → 码 ${outcomes.getValue(it).codes.joinToString()}") }
                becameParcel.forEach { appendLine("  ⛔ 误收成快递 ${it.id} ${it.origin}") }
                pollutedUnrecognized.forEach { appendLine("  ⚠️ 角标噪音 ${it.id} ${it.origin}") }
                if (hits < units.size) {
                    appendLine()
                    appendLine("── 字段错值（进了但抽错，比漏更危险因为它看着可信）──")
                    units.filterNot { it.hit }.forEach { appendLine("  ${it.why}") }
                }
                if (advisory.isNotEmpty()) {
                    appendLine()
                    appendLine("── 存疑语料（真值有争议，不进棘轮分母）──")
                    advisory.forEach { appendLine("  ${it.id} ${it.origin} → ${outcomes.getValue(it).tier}") }
                }
            },
        )
    }

    private fun judge(case: CorpusCase, receivedAt: Long): Outcome =
        when (val outcome = PickupCodeParser.parse(case.toMessage(receivedAt))) {
            is ParseOutcome.Parsed -> Outcome(Tier.PARSED, outcome.parcels, null, outcome.parcels.map { it.code })
            is ParseOutcome.Rejected -> Outcome(Tier.REJECTED, emptyList(), outcome.reason, outcome.codes)
            ParseOutcome.Ignored -> Outcome(Tier.IGNORED, emptyList(), null, emptyList())
        }

    private fun CorpusCase.toMessage(receivedAt: Long) =
        IncomingMessage(sender = sender, body = body, receivedAt = receivedAt, channel = channel)

    /** 每个非 null 的真值字段算一票；多件的必含与禁含各算一票。 */
    private fun scoreFields(case: CorpusCase, parcels: List<ParsedParcel>, receivedAt: Long): List<Hit> {
        val exp = case.expect
        val parcel = parcels.first()
        val codes = parcels.map { it.code }
        val out = mutableListOf<Hit>()

        fun check(field: String, want: String, got: String?, ok: Boolean) {
            out += Hit(
                "$field 命中 · ${case.id}",
                ok,
                "$field：${case.id} 期望 ${oneLine(want)}，实际 ${oneLine(got ?: "null")}",
            )
        }

        exp.code?.let { check("码", it, parcel.code, it == parcel.code) }
        exp.lockerNo?.let { check("柜号", it, parcel.lockerNo, it == parcel.lockerNo) }
        exp.pointName?.let { check("点名", it, parcel.pointName, it == parcel.pointName) }
        exp.kind?.let { check("类型", it.name, parcel.pointKind.name, it == parcel.pointKind) }
        exp.freeUntilHours?.let {
            val want = receivedAt + it * HOUR_MS
            check("免费截止", "${it}h", parcel.freeUntilAt?.minus(receivedAt)?.let { ms -> "${ms / HOUR_MS}h" }, parcel.freeUntilAt == want)
        }
        exp.closesAtMinute?.let {
            val label = clock(it)
            check("关门", label, parcel.closesAtMinuteOfDay?.let(::clock), parcel.closesAtMinuteOfDay == it)
        }
        if (exp.extraCodes.isNotEmpty()) {
            val missing = exp.extraCodes.filterNot { it in codes }
            out += Hit(
                "同条多件 · ${case.id}",
                missing.isEmpty(),
                "同条多件：${case.id} 切出 ${codes.size} 件「${codes.joinToString()}」，还缺 ${missing.joinToString()}",
            )
        }
        if (exp.forbiddenCodes.isNotEmpty()) {
            val bogus = exp.forbiddenCodes.filter { it in codes }
            out += Hit(
                "禁切成件 · ${case.id}",
                bogus.isEmpty(),
                "禁切成件：${case.id} 把 ${bogus.joinToString()} 当成了一件，那是格口号或数量，不是码",
            )
        }
        if (exp.forbiddenInPointName.isNotEmpty()) {
            val dirty = exp.forbiddenInPointName.filter { parcel.pointName.contains(it) }
            out += Hit(
                "点名禁串 · ${case.id}",
                hit = dirty.isEmpty(),
                why = "点名禁串：${case.id} 点名「${oneLine(parcel.pointName)}」里出现了 ${dirty.joinToString()}",
            )
        }
        return out
    }

    /**
     * 被拒的消息是否仍把认出来的码带走了。
     * 拒绝不等于白拒绝：用户补的应该只是「在哪个点」，不是重新抄一遍码。
     */
    private fun refusalCarryUnits(
        scored: List<CorpusCase>,
        outcomes: Map<CorpusCase, Outcome>,
    ): List<Hit> = scored.mapNotNull { case ->
        val want = case.expect.hintCodes
        if (want.isEmpty()) return@mapNotNull null
        val got = outcomes.getValue(case).codes
        val missing = want.filterNot { it in got }
        Hit(
            "拒绝仍带码 · ${case.id}",
            missing.isEmpty(),
            "拒绝仍带码：${case.id} 期望带上 ${want.joinToString()}，实际 ${got.joinToString().ifBlank { "无" }}",
        )
    }

    private data class Hit(val label: String, val hit: Boolean, val why: String)

    private fun rate(part: Int, whole: Int): Double = if (whole == 0) 1.0 else floor(part * 100.0 / whole) / 100.0

    private fun pct(value: Double): String = "${(value * 100).toInt()}％"

    /** 换行折成可见字符：多行点名若不折行，报告里「期望」和「实际」会显示成几乎相同的两行。 */
    private fun oneLine(value: String): String = value.replace("\n", "⏎")

    private fun clock(minuteOfDay: Int): String = "${minuteOfDay / 60}:${"%02d".format(minuteOfDay % 60)}"
}
