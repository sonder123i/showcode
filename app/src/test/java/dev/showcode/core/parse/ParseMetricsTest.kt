package dev.showcode.core.parse

import dev.showcode.core.parse.corpus.ALL
import dev.showcode.core.parse.corpus.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 解析质量的棘轮测试。它不验「这一条对不对」——那是 `PickupCodeParserTest` 的活；
 * 它验的是**整体水平不许倒退**，并把每一条漏项打印成可施工的清单。
 *
 * 报告落在 `app/build/parse-metrics.txt`，跑一次刷新一次。
 */
class ParseMetricsTest {

    private val receivedAt = 1_760_000_000_000L

    @Test
    fun `棘轮 - 四项指标不得低于基线`() {
        val report = ParseMetrics.measure(ALL, receivedAt)
        File("build").mkdirs()
        File("build/parse-metrics.txt").writeText(report.text)

        assertTrue("进门率退化 ${fp(report.gateRecall)} < 基线 ${fp(ParseBaseline.GATE_RECALL)}\n\n${report.text}",
            report.gateRecall + EPS >= ParseBaseline.GATE_RECALL)
        assertTrue("字段准确率退化 ${fp(report.fieldAccuracy)} < 基线 ${fp(ParseBaseline.FIELD_ACCURACY)}\n\n${report.text}",
            report.fieldAccuracy + EPS >= ParseBaseline.FIELD_ACCURACY)
        assertTrue("需确认率升高 ${fp(report.unconfirmedRate)} > 基线 ${fp(ParseBaseline.UNCONFIRMED_RATE)}\n\n${report.text}",
            report.unconfirmedRate <= ParseBaseline.UNCONFIRMED_RATE + EPS)
        assertTrue("无痕蒸发率升高 ${fp(report.silentLoss)} > 基线 ${fp(ParseBaseline.SILENT_LOSS)}\n\n${report.text}",
            report.silentLoss <= ParseBaseline.SILENT_LOSS + EPS)
        assertTrue("误收率升高 ${fp(report.falseIntake)} > 基线 ${fp(ParseBaseline.FALSE_INTAKE)}\n\n${report.text}",
            report.falseIntake <= ParseBaseline.FALSE_INTAKE + EPS)
    }

    @Test
    fun `语料自身 - id 唯一且每条期望都要有考核字段`() {
        assertEquals("语料 id 有重复", ALL.size, ALL.map { it.id }.distinct().size)

        val bare = ALL.filter { it.expect.tier == Tier.PARSED && !hasAnyField(it.expect) }
        assertEquals("这些语料期望解析成功却没考核任何字段，会让指标虚高：$bare", 0, bare.size)

        val badIgnored = ALL.filter {
            it.expect.tier == Tier.IGNORED &&
                (it.expect.code != null || it.expect.pointName != null || it.expect.kind != null)
        }
        assertEquals("期望静默丢弃的语料不该带真值字段：$badIgnored", 0, badIgnored.size)

        // 拒绝不该等于白拒绝：送去确认的消息必须声明它带走了哪些码，
        // 否则用户在手动导入页要从头抄一遍取件码。
        val unconfirmedBlind = ALL.filter {
            it.expect.reason == UnrecognizedReason.POINT_NAME_UNCONFIRMED && it.expect.hintCodes.isEmpty()
        }
        assertEquals("这些语料送去确认却没要求带走码：$unconfirmedBlind", 0, unconfirmedBlind.size)
    }

    private fun hasAnyField(expect: dev.showcode.core.parse.corpus.Expect): Boolean =
        expect.code != null || expect.lockerNo != null || expect.pointName != null ||
            expect.kind != null || expect.freeUntilHours != null || expect.closesAtMinute != null ||
            expect.extraCodes.isNotEmpty() || expect.forbiddenInPointName.isNotEmpty()

    private fun fp(value: Double): String = "${(value * 100).toInt()}％"

    private companion object {
        /** 指标只保留两位小数，比较时给一个百分之一的容差。 */
        const val EPS = 0.011
    }
}
