package dev.showcode.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 磁贴的读数判据。磁贴是唯一一块**没人打开 App 也会被读到**的表面，
 * 所以这里管的是「什么时候不许报数」，不是排版。
 */
class TileStateTest {

    private val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 15)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parcel(id: Long, closes: Int?, picked: Boolean = false) = Parcel(
        id = id, code = "5-2-77$id", lockerNo = null, carrier = "中通快递",
        pointKey = "cndm", pointName = "菜鸟驿站·东门", pointKind = PointKind.STATION,
        owner = Owner.SELF, source = Source.SMS, receivedAt = now,
        freeUntilAt = null, closesAtMinuteOfDay = closes, picked = picked, pickedAt = null, raw = "",
    )

    @Test
    fun `权限没给时不许报数，传进来的数也要归零`() {
        // 那时收件箱根本没在读，「没有待取件」是一块骗人的平安牌。
        val state = tileReadout(notificationGranted = false, pending = 3, urgent = 2)
        assertEquals(TileAction.OPEN_LISTENER_SETTINGS, state.action)
        assertFalse(state.reportCounts)
        assertEquals(0, state.pending)
        assertEquals(0, state.urgent)
    }

    @Test
    fun `权限在时磁贴做一件真事，数照报`() {
        val state = tileReadout(notificationGranted = true, pending = 3, urgent = 1)
        assertEquals(TileAction.REFRESH_NOW, state.action)
        assertTrue(state.reportCounts)
        assertEquals(3, state.pending)
        assertEquals(1, state.urgent)
    }

    @Test
    fun `零件也照常报，磁贴不许变灰`() {
        // 灰掉的磁贴读起来像坏了，而它此刻说的是实话。
        val state = tileReadout(notificationGranted = true, pending = 0, urgent = 0)
        assertTrue(state.reportCounts)
        assertEquals(0, state.pending)
    }

    @Test
    fun `急 含临期与过期风险，安全与已取的不算`() {
        // 磁贴没有颜色，只有字。已经红着的那些如果在唯一的提醒位上消失，就是最坏的那种漏报。
        val dueSoon = parcel(1, closes = 16 * 60)          // 还有 1 小时 → 临期
        val atRisk = parcel(2, closes = 14 * 60)           // 已经过了 → 过期风险
        val safe = parcel(3, closes = 21 * 60)
        val pickedDueSoon = parcel(4, closes = 16 * 60, picked = true)
        assertEquals(2, urgentCount(listOf(dueSoon, atRisk, safe, pickedDueSoon), now))
    }

    @Test
    fun `没有件的时候急件数是零而不是负数或报错`() {
        assertEquals(0, urgentCount(emptyList(), now))
    }
}
