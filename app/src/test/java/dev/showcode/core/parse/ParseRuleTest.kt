package dev.showcode.core.parse

import dev.showcode.core.Owner
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户自定义解析规则。这一层的全部价值系于一条约束：**只做加法**。
 * 所以测试的重心不在「规则能不能命中」，而在「规则会不会伤到本来认得的东西」。
 */
class ParseRuleTest {

    private fun msg(body: String, sender: String = "10690123") = IncomingMessage(
        sender = sender,
        body = body,
        receivedAt = 1_700_000_000_000L,
        channel = Source.SMS,
        owner = Owner.SELF,
    )

    private fun rule(pattern: String, pointName: String = "向阳代收点", lockerNo: String? = null) =
        ParseRule(pattern = pattern, pointName = pointName, lockerNo = lockerNo)

    /**
     * 内置锚定表够不着的写法：锚定词「暗号」不在两张词表里，所以内置一个码都抽不出来。
     * ⚠️ 必须是这种形态——内置只要抽出过码，规则这条分支就走不到（见 `内置认得出的件不被任何规则改写`）。
     */
    private val oddBody = "凭暗号8877到向阳代收点领取"

    /** 内置认得出的标准写法（语料 `fmt-cainiao` 同一条）。 */
    private val normalBody =
        "【菜鸟驿站】您的中通快递包裹已到向阳小区菜鸟驿站，取件码5-2-7712，货架3排12号，营业时间08:00-20:00"

    // ---------- 只做加法 ----------

    @Test
    fun `内置认得出的件不被任何规则改写`() {
        val rules = listOf(rule("取件码\\s*([0-9\\-]+)", "人民路菜鸟驿站"))
        assertEquals(PickupCodeParser.parse(msg(normalBody)), PickupCodeParser.parse(msg(normalBody), rules))
        val parcel = (PickupCodeParser.parse(msg(normalBody), rules) as ParseOutcome.Parsed).parcels.single()
        assertEquals("5-2-7712", parcel.code)
        assertEquals("向阳小区菜鸟驿站", parcel.pointName)
        assertEquals("中通快递", parcel.carrier)
    }

    @Test
    fun `码认得出但点不确定的那些仍然走确认而不是被规则抢走`() {
        val outcome = PickupCodeParser.parse(msg("取件码 7902，请到柜台领取"), listOf(rule("取件码\\s*(\\d{4})", "任意点")))
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(listOf("7902"), (outcome as ParseOutcome.Rejected).codes)
    }

    @Test
    fun `内置落空时规则补出一个件`() {
        val outcome = PickupCodeParser.parse(msg(oddBody), listOf(rule("暗号(\\d{4})")))
        assertTrue(outcome is ParseOutcome.Parsed)
        val parcel = (outcome as ParseOutcome.Parsed).parcels.single()
        assertEquals("8877", parcel.code)
        assertEquals("向阳代收点", parcel.pointName)
        assertNotNull(parcel.closesAtMinuteOfDay)
        assertNull(parcel.freeUntilAt)
        // 规则不带承运商信息，退成「快递」而不是替用户猜一家
        assertEquals("快递", parcel.carrier)
        // 类型按点名判，和内置同一套词表：「代收点」目前不在 SELFPICK 的词表里，退成驿站默认值。
        // 这条不是规则层的决定，改它要动内置的 kindOf，本轮不碰。
        assertEquals(PointKind.STATION, parcel.pointKind)
    }

    @Test
    fun `空规则表时这条消息的结论由内置判据决定`() {
        // 规则的加入不能改变「没有规则时」的任何结论，所以这里只断言两次解析相等。
        assertEquals(PickupCodeParser.parse(msg(oddBody)), PickupCodeParser.parse(msg(oddBody), emptyList()))
        assertTrue(PickupCodeParser.parse(msg(oddBody)) is ParseOutcome.Rejected)
    }

    @Test
    fun `规则不得绕过验证码那道负向锚`() {
        // 用户完全可能写出这么一条命中验证码的正则，而它认出的不是一件快递。
        val rules = listOf(rule("验证码\\s*(\\d{6})", "楼下驿站"))
        assertFalse(PickupCodeParser.parse(msg("验证码 610920，请勿泄露给他人"), rules) is ParseOutcome.Parsed)
    }

    @Test
    fun `禁用的规则不参与补位`() {
        assertFalse(
            PickupCodeParser.parse(msg(oddBody), listOf(rule("暗号(\\d{4})").copy(enabled = false)))
                is ParseOutcome.Parsed,
        )
    }

    @Test
    fun `坏正则静默失效而不是抛出去`() {
        // 录入时会被 validate 拒掉，但库里躺着一条坏行本身不该让一次刷新崩掉。
        assertFalse(PickupCodeParser.parse(msg(oddBody), listOf(rule("([0-9+"))) is ParseOutcome.Parsed)
    }

    // ---------- 命中与形状是两层 ----------

    @Test
    fun `命中返回原样，形状闸门在成卡那一步才生效`() {
        assertEquals("8877", ParseRules.matchCode("暗号([0-9]+)", oddBody))
        // 没有捕获组时取整段命中——这一步确实命中了
        assertEquals("暗号8877", ParseRules.matchCode("暗号[0-9]{4}", oddBody))
        // 但「暗号8877」不是一个码的形状，所以成不了卡
        assertEquals(null, ParseRules.apply(listOf(rule("暗号[0-9]{4}")), oddBody, 1_700_000_000_000L))
    }

    @Test
    fun `形状不合规的串认不成码，手机号也不行`() {
        for (body in listOf("凭暗号" + "1".repeat(13) + "到向阳代收点领取", "凭暗号13800138000到向阳代收点领取", "凭暗号8到向阳代收点领取")) {
            assertEquals(null, ParseRules.apply(listOf(rule("暗号([0-9]+)")), body, 1_700_000_000_000L))
        }
    }

    @Test
    fun `柜号从规则带出来`() {
        val outcome = PickupCodeParser.parse(msg(oddBody), listOf(rule("暗号(\\d{4})", lockerNo = "A12")))
        assertEquals("A12", (outcome as ParseOutcome.Parsed).parcels.single().lockerNo)
    }

    // ---------- 保存前那一眼预览 ----------

    private fun check(pattern: String, body: String, pointName: String = "向阳代收点") =
        ParseRules.check(pattern, pointName, msg(body))

    @Test
    fun `预览说生效的那些真的会补出一个件`() {
        val verdict = check("暗号(\\d{4})", oddBody)
        assertTrue(verdict is RuleCheck.WouldAdd)
        assertEquals("8877", (verdict as RuleCheck.WouldAdd).code)
    }

    @Test
    fun `内置已经认得出的那条，预览要说这条规则永远问不到`() {
        // 这一档和「正则写错了」在用户眼里是同一件事（都不生效），修法却相反：一个该删、一个该改。
        val verdict = check("取件码\\s*([0-9\\-]+)", normalBody)
        assertEquals(RuleCheck.MaskedByBuiltIn, verdict)
    }

    @Test
    fun `码认得出只是点不确定的那些，规则同样用不上`() {
        // 「取件码 7902，请到柜台领取」走的是确认那一支，规则补位发生在更早的岔路口。
        assertEquals(RuleCheck.MaskedByBuiltIn, check("取件码\\s*(\\d{4})", "取件码 7902，请到柜台领取"))
    }

    @Test
    fun `没有捕获组时命中整段，预览当场指出那不是个码`() {
        // 存下去的后果是一条永不生效的规则：形状闸门会在解析时把它拒掉，而列表里看不出异样。
        val verdict = check("暗号[0-9]{4}", oddBody)
        assertEquals(RuleCheck.NotCodeShape("暗号8877"), verdict)
    }

    @Test
    fun `命中不了就说命中不了`() {
        assertEquals(RuleCheck.NoHit, check("凭暗号(\\d{6})", oddBody))
    }

    @Test
    fun `验证码那类正文被语境闸门拦下，预览要说明是被拦不是没命中`() {
        val verdict = check("验证码\\s*(\\d{6})", "验证码 610920，请勿泄露给他人")
        assertEquals(RuleCheck.NoPickupContext, verdict)
    }

    @Test
    fun `写坏的正则由预览先给出拒收理由`() {
        assertEquals(RuleCheck.Invalid(RuleReject.EMPTY_PATTERN), check("", oddBody))
        assertEquals(RuleCheck.Invalid(RuleReject.BAD_SYNTAX), check("([0-9+", oddBody))
        assertEquals(
            RuleCheck.Invalid(RuleReject.EMPTY_POINT_NAME),
            ParseRules.check("暗号(\\d{4})", "  ", msg(oddBody)),
        )
    }

    @Test
    fun `预览也要报条数上限`() {
        // 不然用户按「保存」只会得到一句「没能写进本机数据库」，而他需要的是「先删掉一条」。
        assertEquals(
            RuleCheck.Invalid(RuleReject.TOO_MANY_RULES),
            ParseRules.check("暗号(\\d{4})", "向阳代收点", msg(oddBody), ParseRules.MAX_RULES),
        )
    }

    // ---------- 录入校验 ----------

    @Test
    fun `录入时把坏规则挡在门外`() {
        assertNull(ParseRules.validate("暗号(\\d{4})", "向阳驿站", 0))
        assertEquals(RuleReject.EMPTY_PATTERN, ParseRules.validate("", "向阳驿站", 0))
        assertEquals(RuleReject.BAD_SYNTAX, ParseRules.validate("([0-9+", "向阳驿站", 0))
        assertEquals(RuleReject.EMPTY_POINT_NAME, ParseRules.validate("8877", "  ", 0))
        assertEquals(
            RuleReject.PATTERN_TOO_LONG,
            ParseRules.validate("a".repeat(ParseRules.MAX_PATTERN_LENGTH + 1), "向阳驿站", 0),
        )
        assertEquals(RuleReject.POINT_NAME_TOO_LONG, ParseRules.validate("8877", "路".repeat(41), 0))
        assertEquals(RuleReject.TOO_MANY_RULES, ParseRules.validate("8877", "向阳驿站", ParseRules.MAX_RULES))
    }

    // ---------- 从原文生成规则 ----------

    @Test
    fun `生成的规则被它诞生的那条文本命中`() {
        val generated = ParseRules.literalRule(oddBody, "8877", "向阳代收点")
        assertNotNull(generated)
        val outcome = PickupCodeParser.parse(msg(oddBody), listOf(generated!!))
        assertTrue(outcome is ParseOutcome.Parsed)
        // 捕获组取到的必须是码本身，不是整段命中——「暗号8877」是要念给柜机键盘听的串
        assertEquals("8877", (outcome as ParseOutcome.Parsed).parcels.single().code)
    }

    @Test
    fun `生成的规则带锚定词所以不会在任何提到这个码的文本里命中`() {
        val generated = ParseRules.literalRule(oddBody, "8877", "向阳代收点")!!
        assertTrue(generated.pattern.contains("暗号"))
        // 别的写法、同一个码：锚定词不同，所以这条规则不该抢它
        assertEquals(null, ParseRules.matchCode(generated.pattern, "取件密码8877已失效"))
    }

    @Test
    fun `生成的规则不抄整条原文`() {
        // 抄整句的规则在措辞一变之后就永不命中，而它留在列表里看起来是在工作的。
        val generated = ParseRules.literalRule(oddBody, "8877", "向阳代收点")!!
        assertFalse(generated.pattern.contains(Regex.escape(oddBody)))
        assertTrue(generated.pattern.length < oddBody.length + 20)
    }

    @Test
    fun `码后面的措辞变了仍然命中，因为上下文只取左边`() {
        // 柜号、货架号、提示语全长在码**后面**，而且每家写法不同；带上右边就是脆的。
        // 码与锚定词之间多出来的空格要容忍（「请凭数字密码 8-9-0765」是真写法）。
        val generated = ParseRules.literalRule(oddBody, "8877", "向阳代收点")!!
        assertEquals("8877", ParseRules.matchCode(generated.pattern, "凭暗号8877，请到 3 号柜领取"))
        assertEquals("8877", ParseRules.matchCode(generated.pattern, "凭暗号 8877 到向阳代收点领取"))
    }

    @Test
    fun `锚定词本身被改写时不命中，这是预览要兜的那一格`() {
        // 记下这个边界而不是假装规则能兜住一切措辞：真正的兜底是保存前的那一眼预览。
        val generated = ParseRules.literalRule(oddBody, "8877", "向阳代收点")!!
        assertEquals(null, ParseRules.matchCode(generated.pattern, "凭、暗号8877到向阳代收点领取"))
    }

    @Test
    fun `码顶在句首时改用右边的上下文`() {
        val generated = ParseRules.literalRule("8877到向阳代收点领取", "8877", "向阳代收点")
        assertNotNull(generated)
        assertEquals("8877", ParseRules.matchCode(generated!!.pattern, "8877到向阳代收点领取"))
        // 码两侧都只剩空白时确实没有可锚的东西——宁可不生成，也不要生成一条「看到码就命中」的规则
        assertEquals(null, ParseRules.literalRule("8877 到向阳代收点领取", "8877", "向阳代收点"))
    }

    @Test
    fun `原文只有码本身时不生成规则`() {
        assertEquals(null, ParseRules.literalRule("8877", "8877", "向阳代收点"))
        assertEquals(null, ParseRules.literalRule("别的文本", "8877", "向阳代收点"))
    }

    // ---------- 重判 ----------

    @Test
    fun `库里挂着的未识别在补规则之后被重新认出来`() {
        val records = listOf(UnrecognizedRecord(1, "1069", oddBody, 1_700_000_000_000L))
        assertEquals(listOf(1L), ParseRules.reprocessTargets(records, listOf(rule("暗号(\\d{4})"))).map { it.id })
    }

    @Test
    fun `没有启用中的规则时重判一条都不试`() {
        val records = listOf(UnrecognizedRecord(1, "1069", oddBody, 1_700_000_000_000L))
        assertTrue(ParseRules.reprocessTargets(records, listOf(rule("暗号(\\d{4})").copy(enabled = false))).isEmpty())
        assertTrue(ParseRules.reprocessTargets(records, emptyList()).isEmpty())
    }

    @Test
    fun `重判认不出的那些留在列表里`() {
        val records = listOf(UnrecognizedRecord(1, "1069", "这条真的没有码", 1_700_000_000_000L))
        assertTrue(ParseRules.reprocessTargets(records, listOf(rule("暗号(\\d{4})"))).isEmpty())
    }
}
