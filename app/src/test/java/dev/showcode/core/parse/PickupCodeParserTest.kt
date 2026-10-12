package dev.showcode.core.parse

import dev.showcode.core.PointKind
import dev.showcode.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实格式的中文短信做样本。story-2 的技术成功率最低，
 * 而失败是必然且已知的——所以这里既测「认出来的」，也测「认不出来时必须说清为什么」。
 */
class PickupCodeParserTest {

    private val now = 1_760_000_000_000L

    private fun sms(sender: String, body: String) =
        IncomingMessage(sender = sender, body = body, receivedAt = now, channel = Source.SMS)

    private fun parsed(sender: String, body: String): ParsedParcel {
        val outcome = PickupCodeParser.parse(sms(sender, body))
        assertTrue("期望解析成功，实际 $outcome", outcome is ParseOutcome.Parsed)
        // 单件断言只看第一件；一条多件的切分由语料棘轮（ParseMetricsTest）考核。
        return (outcome as ParseOutcome.Parsed).parcels.first()
    }

    @Test
    fun `菜鸟驿站 - 取件码与货架号都抽出来`() {
        val p = parsed(
            "106901234",
            "【菜鸟驿站】您的中通快递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-2-3391，货架3排12号，请在营业时间前来取件。",
        )
        assertEquals("5-2-3391", p.code)
        assertEquals("3排12号", p.lockerNo)
        assertEquals("中通快递", p.carrier)
        assertEquals("菜鸟驿站(阳光花园小区东门店)", p.pointName)
        assertEquals(PointKind.STATION, p.pointKind)
    }

    @Test
    fun `丰巢 - 格口号、免费时长、18小时截止时刻`() {
        val p = parsed(
            "106575580",
            "【丰巢】您的顺丰速运快递已放入丰巢智能柜(阳光花园3号楼柜)，格口A12，取件码：8-3-1024，18小时内免费保管，超时将收取保管费。",
        )
        assertEquals("8-3-1024", p.code)
        assertEquals("A12", p.lockerNo)
        assertEquals("顺丰速运", p.carrier)
        assertEquals("丰巢智能柜(阳光花园3号楼柜)", p.pointName)
        assertEquals(PointKind.LOCKER, p.pointKind)
        assertEquals(now + 18 * 3600_000L, p.freeUntilAt)
        assertNull(p.closesAtMinuteOfDay)
    }

    @Test
    fun `丰巢 - 短信写了别的免费时长就以短信为准`() {
        val p = parsed("106575580", "【丰巢】快递已放入丰巢智能柜(阳光花园3号楼柜)，取件码1234，24小时内免费保管。")
        assertEquals(now + 24 * 3600_000L, p.freeUntilAt)
    }

    @Test
    fun `京东自提点 - 识别为自提点而不是驿站`() {
        val p = parsed(
            "10691777",
            "【京东物流】您的包裹已到达京东自提点(好邻居便利店)，取件码JD7739201，货架B-4，请及时领取。",
        )
        assertEquals("JD7739201", p.code)
        assertEquals("B-4", p.lockerNo)
        assertEquals("京东物流", p.carrier)
        assertEquals(PointKind.SELFPICK, p.pointKind)
    }

    @Test
    fun `短信没有货架号 - lockerNo 为 null 而不是空串或占位符`() {
        // boundary-null：现场模式据此整块隐藏柜号区并放大取件码，绝不显示 "--"
        val p = parsed(
            "106901234",
            "【菜鸟驿站】您的圆通速递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-2-3408，请及时领取。",
        )
        assertNull(p.lockerNo)
    }

    @Test
    fun `营业时间 - 取区间结束时刻作为关门时间`() {
        val p = parsed(
            "106901234",
            "【菜鸟驿站】您的包裹已到达菜鸟驿站(东门店)，取件码5-1-2277，营业时间09:00-20:30，请及时领取。",
        )
        assertEquals(20 * 60 + 30, p.closesAtMinuteOfDay)
    }

    @Test
    fun `X点前取件 - 解析为关门时间`() {
        val p = parsed("10691777", "【京东】您的包裹已到达京东自提点(好邻居便利店)，取件码JD123，请在21点前取件。")
        assertEquals(21 * 60, p.closesAtMinuteOfDay)
    }

    @Test
    fun `短信没写营业时间 - 退到该类型的内置默认`() {
        val station = parsed("106901234", "【菜鸟驿站】包裹已到达菜鸟驿站(东门店)，取件码5-1-2277。")
        assertEquals(21 * 60, station.closesAtMinuteOfDay)
    }

    @Test
    fun `手机号不会被当成取件码`() {
        val outcome = PickupCodeParser.parse(
            sms("106901234", "【菜鸟驿站】包裹已到达菜鸟驿站(东门店)，取件码13800138000，请联系快递员。"),
        )
        assertTrue(outcome is ParseOutcome.Rejected)
        assertEquals(UnrecognizedReason.NO_CODE_FOUND, (outcome as ParseOutcome.Rejected).reason)
    }

    // ── 认不出来时必须说清漏了什么（brief 设计标准：不说「权限不足」） ──

    @Test
    fun `码在小程序里 - 给出指向小程序的原因`() {
        val outcome = PickupCodeParser.parse(
            sms("106901234", "【菜鸟驿站】您的包裹已到站，请在小程序内查看取件凭证，凭码取件。"),
        )
        assertEquals(
            UnrecognizedReason.CODE_LIVES_IN_APP,
            (outcome as ParseOutcome.Rejected).reason,
        )
    }

    @Test
    fun `5G网络短信无发件人号码 - 给出无发件人号码的原因`() {
        val outcome = PickupCodeParser.parse(
            sms("", "5G消息：丰巢快递柜投递成功，点击卡片查看取件码"),
        )
        assertEquals(
            UnrecognizedReason.NO_SENDER_NUMBER,
            (outcome as ParseOutcome.Rejected).reason,
        )
    }

    // ── 不是取件消息的一律静默丢弃，否则「未识别」角标会常年无意义 ──

    @Test
    fun `营销短信被忽略而不是记为未识别`() {
        val outcome = PickupCodeParser.parse(
            sms("106900000", "【天猫】双11狂欢节全场5折起，点击领取优惠券 http://tb.cn/x"),
        )
        assertEquals(ParseOutcome.Ignored, outcome)
    }

    @Test
    fun `物流轨迹通知被忽略 - 它不是取件消息`() {
        val outcome = PickupCodeParser.parse(
            sms("106900000", "【顺丰速运】您的快件已到达杭州中转中心，正在派送中，请保持电话畅通。"),
        )
        assertEquals(ParseOutcome.Ignored, outcome)
    }

    @Test
    fun `派送成功通知被忽略`() {
        val outcome = PickupCodeParser.parse(
            sms("106900000", "【京东物流】您的订单已签收，感谢使用京东，期待再次为您服务。"),
        )
        assertEquals(ParseOutcome.Ignored, outcome)
    }

    @Test
    fun `认不出具体门店时不再合成一张卡，而是带着码送确认`() {
        val outcome = PickupCodeParser.parse(
            sms("106575580", "【丰巢】您有一个快递已入柜，取件码6-1-0088，请及时取件。"),
        )
        assertTrue("期望送确认，实际 $outcome", outcome is ParseOutcome.Rejected)
        val rejected = outcome as ParseOutcome.Rejected
        assertEquals(UnrecognizedReason.POINT_NAME_UNCONFIRMED, rejected.reason)
        // 「丰巢快递柜」会把小区门口两台柜机合成一张卡，所以不再退化成那种看着正常的名字。
        // 但码是确定的——不带下去就等于让用户在手动导入页重新抄一遍。
        assertEquals(listOf("6-1-0088"), rejected.codes)
    }
}
