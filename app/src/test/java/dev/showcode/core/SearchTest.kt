package dev.showcode.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检索判据。重心不是「搜得到搜不到」，而是三件事：
 * **匹配哪些字段是闭集**、**过滤不许重排**、**空结果必须能区分「没有」和「你已经取过了」**。
 */
class SearchTest {

    /** 固定在今天 15:00：21:00 关门是安全，16:00 关门是临期，14:00 关门是过期风险。 */
    private val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 15)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val dayMs = 24L * 60 * 60 * 1000

    private fun parcel(
        id: Long,
        code: String,
        pointKey: String,
        pointName: String,
        carrier: String = "中通快递",
        lockerNo: String? = null,
        kind: PointKind = PointKind.STATION,
        closesAtMinuteOfDay: Int? = 21 * 60,
        freeUntilAt: Long? = null,
        receivedAt: Long = now,
        picked: Boolean = false,
        raw: String = "营业时间08:00-20:00，运单号77120099887766",
    ) = Parcel(
        id = id, code = code, lockerNo = lockerNo, carrier = carrier,
        pointKey = pointKey, pointName = pointName, pointKind = kind,
        owner = Owner.SELF, source = Source.SMS, receivedAt = receivedAt,
        freeUntilAt = freeUntilAt, closesAtMinuteOfDay = closesAtMinuteOfDay,
        picked = picked, pickedAt = null, raw = raw,
    )

    private fun station(id: Long, code: String = "5-2-77$id", receivedAt: Long = now, picked: Boolean = false) =
        parcel(id = id, code = code, pointKey = "cndm", pointName = "菜鸟驿站·东门",
            receivedAt = receivedAt, picked = picked)

    private fun locker(id: Long, code: String = "8-3-10$id") = parcel(
        id = id, code = code, pointKey = "fc3", pointName = "丰巢柜·3号楼",
        kind = PointKind.LOCKER, closesAtMinuteOfDay = null,
        freeUntilAt = now + 3 * 60 * 60 * 1000, carrier = "顺丰速运", lockerNo = "A12",
    )

    private fun groupsOf(vararg parcels: Parcel, aliases: Map<String, PointAlias> = emptyMap()) =
        groupByPoint(parcels.toList(), now, aliases = aliases)

    private fun search(groups: List<PickupPointGroup>, query: String, window: SearchWindow = SearchWindow.ALL, picked: List<Parcel> = emptyList()) =
        searchParcels(groups, picked, query, window, now)

    // ---------- 匹配的是哪几个字段 ----------

    @Test
    fun `空白查询原样返回，不加任何过滤`() {
        val groups = groupsOf(station(1), locker(2))
        assertEquals(groups, search(groups, "").groups)
        assertEquals(groups, search(groups, "   ").groups)
    }

    @Test
    fun `码、点名、承运商、柜号都能搜到`() {
        val groups = groupsOf(station(1, code = "5-2-7712"), locker(2, code = "8-3-1024"))
        assertEquals(1, search(groups, "7712").matchedParcels)
        assertEquals(1, search(groups, "东门").matchedParcels)
        assertEquals(1, search(groups, "中通").matchedParcels)
        assertEquals(1, search(groups, "A12").matchedParcels)
    }

    @Test
    fun `不带分隔符搜码也要命中，人记的是那一串数字`() {
        val groups = groupsOf(station(1, code = "5-2-7712"))
        assertEquals(1, search(groups, "527712").matchedParcels)
        assertEquals(1, search(groups, "52").matchedParcels)
    }

    @Test
    fun `全角数字与大小写都不该影响命中`() {
        val groups = groupsOf(station(1, code = "5-2-7712"), locker(2))
        assertEquals(1, search(groups, "５-２-７７１２").matchedParcels)
        assertEquals(1, search(groups, "a12").matchedParcels)
    }

    @Test
    fun `合并过的点用组显示名搜得到，用旧写法也搜得到`() {
        val alias = PointAlias("cndm", "旧写法", "renmin", "人民路驿站", now)
        val groups = groupsOf(
            parcel(id = 1, code = "5-2-7712", pointKey = "cndm", pointName = "东门菜鸟驿站(旧写法)"),
            aliases = mapOf("cndm" to alias),
        )
        assertEquals("人民路驿站", groups.single().name)
        assertEquals(1, search(groups, "人民路").matchedParcels)
        assertEquals(1, search(groups, "旧写法").matchedParcels)
        assertTrue(matchesQuery(groups.single().items.single(), groups.single().name, "人民路"))
    }

    @Test
    fun `原文里的营业时间和运单号不参与匹配`() {
        // 这是闭集的全部意义：误命中的代价是他走到错的点，而漏命中只是再搜一次。
        val groups = groupsOf(station(1))
        assertTrue(search(groups, "08:00").isEmpty)
        assertTrue(search(groups, "77120099887766").isEmpty)
    }

    // ---------- 过滤不许重排 ----------

    @Test
    fun `过滤只删不改顺序：组间与组内都是原序`() {
        // 三件都命中同一个查询，所以这一条测的不是过滤，是「过滤不许顺手重排」。
        // 过期风险那一组在输入里就排最前（groupByPoint 的排序结果）。
        val atRiskLocker = parcel(
            id = 3, code = "8-3-1003", pointKey = "fc3", pointName = "丰巢柜·3号楼",
            kind = PointKind.LOCKER, closesAtMinuteOfDay = null, freeUntilAt = now - HOUR_MS,
            carrier = "中通快递",
        )
        val groups = groupsOf(station(1), station(2), atRiskLocker)
        val result = search(groups, "中通")
        assertEquals(listOf("fc3", "cndm"), result.groups.map { it.key })
        assertEquals(listOf(1L, 2L), result.groups.last().items.map { it.id })
    }

    @Test
    fun `一件都不剩的组直接消失，不留一张空卡`() {
        val result = search(groupsOf(station(1), locker(2)), "东门")
        assertEquals(1, result.groups.size)
        assertEquals("cndm", result.groups.single().key)
    }

    @Test
    fun `worst 跟着剩下的件重算，不给已经没有急件的卡标红`() {
        val earlyClosing = parcel(id = 1, code = "8-1-0001", pointKey = "cndm", pointName = "菜鸟驿站·东门", closesAtMinuteOfDay = 14 * 60)
        val late = parcel(id = 2, code = "5-2-7712", pointKey = "cndm", pointName = "菜鸟驿站·东门", closesAtMinuteOfDay = 21 * 60)
        val groups = groupsOf(earlyClosing, late)
        assertEquals(Urgency.AT_RISK, groups.single().worst)
        val result = search(groups, "7712")
        assertEquals(Urgency.SAFE, result.groups.single().worst)
    }

    // ---------- 时间窗 ----------

    @Test
    fun `今天到把昨天到的挡掉`() {
        val groups = groupsOf(station(1, receivedAt = now - dayMs - 60_000), station(2, receivedAt = now))
        assertEquals(2, search(groups, "", SearchWindow.ALL).matchedParcels)
        assertEquals(1, search(groups, "", SearchWindow.TODAY).matchedParcels)
    }

    @Test
    fun `该去的那一档含过期风险，不只是临期`() {
        val dueSoon = parcel(id = 1, code = "5-1-2201", pointKey = "cndm", pointName = "菜鸟驿站·东门", closesAtMinuteOfDay = 16 * 60)
        val atRisk = parcel(id = 2, code = "5-1-2202", pointKey = "cndm", pointName = "菜鸟驿站·东门", closesAtMinuteOfDay = 14 * 60)
        val safe = parcel(id = 3, code = "5-1-2203", pointKey = "cndm", pointName = "菜鸟驿站·东门", closesAtMinuteOfDay = 21 * 60)
        val result = search(groupsOf(dueSoon, atRisk, safe), "", SearchWindow.URGENT)
        assertEquals(setOf(1L, 2L), result.groups.single().items.map { it.id }.toSet())
    }

    // ---------- 空结果的两种，必须分清 ----------

    @Test
    fun `一件都没有时，如果那串在已取里就说出来`() {
        val groups = groupsOf(station(1, code = "5-2-7712"))
        val pickedOne = station(9, code = "8-8-4404", picked = true)
        val miss = search(groups, "4404", picked = listOf(pickedOne))
        assertTrue(miss.isEmpty)
        assertEquals(1, miss.pickedMatches)
    }

    @Test
    fun `谁都没命中的那串，pickedMatches 是 0`() {
        val groups = groupsOf(station(1))
        val miss = search(groups, "根本不存在的串", picked = listOf(station(9, picked = true)))
        assertEquals(0, miss.pickedMatches)
    }

    @Test
    fun `空白查询不报已取，那是统计不是检索结果`() {
        val groups = groupsOf(station(1))
        val result = search(groups, "", picked = listOf(station(9, picked = true), station(10, picked = true)))
        assertEquals(0, result.pickedMatches)
        assertFalse(result.isEmpty)
    }
}
