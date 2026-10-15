package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「最近已取」那一栏的判据。它唯一的职责是**误触的补救出口**，所以重心在两件事：
 * 窗口要短到不变成历史堆，以及**取走时间没记上的那条不许出现**（它没有可反悔的时点，
 * 摆在栏里只会让人以为还能改回）。
 */
class RecentlyPickedTest {

    private val hourMs = 60L * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun picked(id: Long, at: Long?) = Parcel(
        id = id,
        code = "5-4-$id",
        lockerNo = null,
        carrier = "中通快递",
        pointKey = "pk",
        pointName = "文昌苑菜鸟驿站",
        pointKind = PointKind.STATION,
        closesAtMinuteOfDay = 21 * 60,
        freeUntilAt = null,
        receivedAt = at ?: now,
        picked = true,
        pickedAt = at,
        owner = Owner.SELF,
        source = Source.SMS,
        raw = "取件码",
    )

    @Test
    fun `窗口内按取走时间倒序，窗口外一条都不留`() {
        val fresh = picked(1, now - hourMs)
        val older = picked(2, now - 3 * hourMs)
        val out = picked(3, now - 7 * hourMs)
        assertEquals(listOf(1L, 2L), recentlyPicked(listOf(older, fresh, out), now).map { it.id })
    }

    @Test
    fun `边界是窗口本身，不是四舍五入`() {
        val inside = picked(1, now - RECENTLY_PICKED_WINDOW_MS + 1)
        val outside = picked(2, now - RECENTLY_PICKED_WINDOW_MS)
        assertEquals(listOf(1L), recentlyPicked(listOf(inside, outside), now).map { it.id })
    }

    @Test
    fun `标了已取却没记时间的不许出现`() {
        assertEquals(emptyList<Long>(), recentlyPicked(listOf(picked(1, null)), now).map { it.id })
    }

    @Test
    fun `它是补救出口不是主路径，所以有条数上限`() {
        val many = (1L..9L).map { picked(it, now - it * hourMs) }
        assertEquals(RECENTLY_PICKED_LIMIT, recentlyPicked(many, now).size)
        // 留下的必须是最近那几条——按时间倒序之后截断，不是按 id。
        assertEquals((1L..RECENTLY_PICKED_LIMIT).toList(), recentlyPicked(many, now).map { it.id })
    }

    @Test
    fun `没被取走的件一条都不属于这一栏`() {
        val stillWaiting = Parcel(
            id = 1, code = "5-4-1", lockerNo = null, carrier = "中通快递",
            pointKey = "pk", pointName = "文昌苑菜鸟驿站", pointKind = PointKind.STATION,
            closesAtMinuteOfDay = 21 * 60, freeUntilAt = null, receivedAt = now,
            picked = false, pickedAt = null, owner = Owner.SELF, source = Source.SMS, raw = "取件码",
        )
        assertEquals(emptyList<Long>(), recentlyPicked(listOf(stillWaiting), now).map { it.id })
    }
}
