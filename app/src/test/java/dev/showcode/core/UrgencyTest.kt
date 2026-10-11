package dev.showcode.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class UrgencyTest {

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun locker(now: Long, freeUntilAt: Long?) = Parcel(
        id = 1, code = "8-3-1024", lockerNo = "A12", carrier = "顺丰速运",
        pointKey = "fc3", pointName = "丰巢柜·3号楼", pointKind = PointKind.LOCKER,
        owner = Owner.SELF, source = Source.SMS, receivedAt = now - 12 * HOUR_MS,
        freeUntilAt = freeUntilAt, closesAtMinuteOfDay = null,
        picked = false, pickedAt = null, raw = "",
    )

    private fun station(now: Long, closesAtMinuteOfDay: Int?) = Parcel(
        id = 2, code = "5-2-3391", lockerNo = null, carrier = "中通快递",
        pointKey = "cndm", pointName = "菜鸟驿站·东门", pointKind = PointKind.STATION,
        owner = Owner.SELF, source = Source.SMS, receivedAt = now - 3 * HOUR_MS,
        freeUntilAt = null, closesAtMinuteOfDay = closesAtMinuteOfDay,
        picked = false, pickedAt = null, raw = "",
    )

    // ── 快递柜：默认临期阈值 = 剩余免费 ≤6 小时 ──

    @Test
    fun `柜子剩5小时是临期`() {
        val now = at(15)
        assertEquals(Urgency.DUE_SOON, urgencyOf(locker(now, now + 5 * HOUR_MS), now))
    }

    @Test
    fun `柜子剩7小时还是安全`() {
        val now = at(15)
        assertEquals(Urgency.SAFE, urgencyOf(locker(now, now + 7 * HOUR_MS), now))
    }

    @Test
    fun `柜子已开始计费就是过期风险`() {
        val now = at(15)
        assertEquals(Urgency.AT_RISK, urgencyOf(locker(now, now - 1 * HOUR_MS), now))
    }

    @Test
    fun `柜子正好卡在阈值上算临期`() {
        val now = at(15)
        assertEquals(Urgency.DUE_SOON, urgencyOf(locker(now, now + 6 * HOUR_MS), now))
    }

    @Test
    fun `阈值可调 - 改成2小时后剩3小时不再算临期`() {
        val now = at(15)
        val p = locker(now, now + 3 * HOUR_MS)
        val tight = UrgencyThresholds(lockerDueSoonMs = 2 * HOUR_MS)
        assertEquals(Urgency.SAFE, urgencyOf(p, now, tight))
        assertEquals(Urgency.DUE_SOON, urgencyOf(p, now))
    }

    // ── 驿站：默认临期阈值 = 关门前 ≤90 分钟 ──

    @Test
    fun `驿站关门前1小时是临期`() {
        val now = at(20, 0)
        assertEquals(Urgency.DUE_SOON, urgencyOf(station(now, 21 * 60), now))
    }

    @Test
    fun `驿站关门前3小时还是安全`() {
        val now = at(18, 0)
        assertEquals(Urgency.SAFE, urgencyOf(station(now, 21 * 60), now))
    }

    @Test
    fun `驿站已关门就是过期风险`() {
        val now = at(21, 30)
        assertEquals(Urgency.AT_RISK, urgencyOf(station(now, 21 * 60), now))
    }

    @Test
    fun `驿站阈值可调 - 改成30分钟后关门前1小时不再算临期`() {
        val now = at(20, 0)
        val p = station(now, 21 * 60)
        val tight = UrgencyThresholds(stationDueSoonMinutes = 30)
        assertEquals(Urgency.SAFE, urgencyOf(p, now, tight))
        assertEquals(Urgency.DUE_SOON, urgencyOf(p, now))
    }

    // ── 没有时效依据时一律安全：不编造数据，也不制造焦虑 ──

    @Test
    fun `没有截止时刻的件永远是安全`() {
        val now = at(20, 0)
        assertEquals(Urgency.SAFE, urgencyOf(locker(now, null), now))
        assertEquals(Urgency.SAFE, urgencyOf(station(now, null), now))
    }

    // ── 倒计时：三态下唯一的紧迫度梯度载体，必须逐秒连续变化 ──

    @Test
    fun `有小时就只显示到分`() {
        val c = countdownOf(4 * HOUR_MS + 12 * MINUTE_MS + 33_000)
        assertEquals(Countdown(hours = 4, minutes = 12, seconds = 33), c)
        assertEquals(true, c.usesHours)
    }

    @Test
    fun `不足1小时才显示到秒`() {
        val c = countdownOf(38 * MINUTE_MS + 7_000)
        assertEquals(Countdown(hours = 0, minutes = 38, seconds = 7), c)
        assertEquals(false, c.usesHours)
    }

    @Test
    fun `剩余为负不会给出负数倒计时`() {
        assertEquals(Countdown(0, 0, 0), countdownOf(-5_000))
    }

    @Test
    fun `已存放天数按整天算`() {
        val now = at(12)
        val p = station(now, null).copy(receivedAt = now - 3 * DAY_MS - 2 * HOUR_MS)
        assertEquals(3, staleDaysOf(p, now))
    }
}
