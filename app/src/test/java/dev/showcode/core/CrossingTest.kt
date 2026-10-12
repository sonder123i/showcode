package dev.showcode.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「下一个状态会变化的时刻」。桌面组件的唤醒就靠它排：
 * 以前只有**已经临期**的件才会挂 60 秒闹钟，于是「安全 → 临期」那一刻没有任何东西被安排醒来，
 * 组件要等到系统下一次 30 分钟周期更新才跟上——而临期提醒正是这个 App 的差异化。
 */
class CrossingTest {

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun locker(now: Long, freeUntilAt: Long?, picked: Boolean = false) = Parcel(
        id = 1, code = "8-3-1024", lockerNo = "A12", carrier = "顺丰速运",
        pointKey = "fc3", pointName = "丰巢柜·3号楼", pointKind = PointKind.LOCKER,
        owner = Owner.SELF, source = Source.SMS, receivedAt = now - 4 * HOUR_MS,
        freeUntilAt = freeUntilAt, closesAtMinuteOfDay = null,
        picked = picked, pickedAt = null, raw = "",
    )

    private fun station(now: Long, closesAtMinuteOfDay: Int?, picked: Boolean = false) = Parcel(
        id = 2, code = "5-2-3391", lockerNo = null, carrier = "中通快递",
        pointKey = "cndm", pointName = "菜鸟驿站·东门", pointKind = PointKind.STATION,
        owner = Owner.SELF, source = Source.SMS, receivedAt = now - HOUR_MS,
        freeUntilAt = null, closesAtMinuteOfDay = closesAtMinuteOfDay,
        picked = picked, pickedAt = null, raw = "",
    )

    @Test
    fun `柜子还剩10小时 - 下一个跨界是4小时后进入临期`() {
        val now = at(15)
        assertEquals(now + 4 * HOUR_MS, nextCrossingAt(listOf(locker(now, now + 10 * HOUR_MS)), now))
    }

    @Test
    fun `已经临期的柜子 - 下一个跨界是免费到期那一刻，不会返回过去的时刻`() {
        val now = at(15)
        assertEquals(now + 2 * HOUR_MS, nextCrossingAt(listOf(locker(now, now + 2 * HOUR_MS)), now))
    }

    @Test
    fun `驿站21点关门、阈值90分钟 - 跨界在19点30`() {
        val now = at(15)
        assertEquals(at(19, 30), nextCrossingAt(listOf(station(now, 21 * 60)), now))
    }

    @Test
    fun `没有任何待取件时不安排唤醒 - 不空转耗电`() {
        val now = at(15)
        val done = locker(now, now + 3 * HOUR_MS, picked = true)
        assertNull(nextCrossingAt(listOf(done), now))
    }

    @Test
    fun `没有时效依据的件不算跨界 - 没有依据就不制造焦虑`() {
        val now = at(15)
        assertNull(nextCrossingAt(listOf(station(now, null), locker(now, null)), now))
    }

    @Test
    fun `多件取最早的那个跨界`() {
        val now = at(15)
        val parcels = listOf(
            station(now, 21 * 60),                  // 21:00 关门，阈值 90 分 → 19:30 进临期
            locker(now, now + 10 * HOUR_MS),        // 25:00 免费到期，阈值 6 小时 → 19:00 进临期
            station(now, 23 * 60),                  // 23:00 关门 → 21:30 进临期
        )
        assertEquals(now + 4 * HOUR_MS, nextCrossingAt(parcels, now))
    }

    @Test
    fun `阈值调早之后跨界时刻也跟着提前`() {
        val now = at(15)
        val thresholds = UrgencyThresholds(lockerDueSoonMs = 9 * HOUR_MS)
        // 剩 10 小时、阈值 9 小时 → 1 小时后就该进临期
        assertEquals(now + HOUR_MS, nextCrossingAt(listOf(locker(now, now + 10 * HOUR_MS)), now, thresholds))
    }
}
