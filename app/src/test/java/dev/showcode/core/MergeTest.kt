package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * story-6 的合并引擎。这里守的是那条锁定的不对称：
 * 错合并会让现场少显示一个码（灾难），不合并只是首页多一张卡片（难看），
 * 所以「拆开必须无损」是结构性的，不是靠回滚写回正确的值。
 */
class MergeTest {

    private val t0 = 1_700_000_000_000L

    private fun alias(source: String, target: String, at: Long = t0) = PointAlias(
        aliasKey = source,
        aliasName = "$source-名",
        targetKey = target,
        targetName = "$target-名",
        createdAt = at,
    )

    private fun mapOf(vararg pairs: Pair<String, PointAlias>) = pairs.toMap()

    private fun parcel(id: Long, key: String, name: String, kind: PointKind = PointKind.STATION, at: Long = t0) =
        Parcel(
            id = id, code = "code$id", lockerNo = null, carrier = "中通快递",
            pointKey = key, pointName = name, pointKind = kind,
            owner = Owner.SELF, source = Source.SMS, receivedAt = at,
            freeUntilAt = null, closesAtMinuteOfDay = 21 * 60,
            picked = false, pickedAt = null, raw = "",
        )

    private fun group(key: String, vararg names: String) = PickupPointGroup(
        key = key,
        name = names.firstOrNull() ?: key,
        kind = PointKind.STATION,
        closesAtMinuteOfDay = 21 * 60,
        items = names.mapIndexed { i, n -> parcel(i + 1L, key, n, at = t0 + i) },
        worst = Urgency.SAFE,
    )

    // ---------- 别名解析 ----------

    @Test
    fun `别名顺链解析到底`() {
        val aliases = mapOf("a" to alias("a", "b"), "b" to alias("b", "c"))
        assertEquals("c", resolvePointKey("a", aliases))
        assertEquals("c", resolvePointKey("c", aliases))
    }

    @Test
    fun `环不会让解析卡死`() {
        val aliases = mapOf("a" to alias("a", "b"), "b" to alias("b", "a"))
        val resolved = resolvePointKey("a", aliases)
        assertTrue(resolved == "a" || resolved == "b")
    }

    @Test
    fun `已经同点的两个键不该再合并`() {
        val aliases = mapOf("a" to alias("a", "b"))
        assertTrue(isSamePoint("a", "b", aliases))
        assertFalse(isSamePoint("a", "z", aliases))
    }

    @Test
    fun `合并后的显示名用目标点而不是最新短信的写法`() {
        val aliases = mapOf("a" to alias("a", "b"))
        assertEquals("b-名", canonicalNameOf("b", aliases.values))
        assertNull(canonicalNameOf("z", aliases.values))
        // 同一目标上有过两次登记时，用最近那次记下的名字
        val newer = mapOf("a" to alias("a", "b", at = t0), "x" to alias("x", "b", at = t0 + 100))
        assertEquals("b-名", canonicalNameOf("b", newer.values))
    }

    // ---------- 分组 ----------

    @Test
    fun `合并把两张卡片变成一张且件数相加`() {
        val parcels = listOf(parcel(1, "a", "XX便利店"), parcel(2, "a", "XX便利店"), parcel(3, "b", "菜鸟驿站XX店"))
        val before = groupByPoint(parcels, t0)
        assertEquals(2, before.size)

        val aliases = mapOf("a" to alias("a", "b"))
        val after = groupByPoint(parcels, t0, aliases = aliases)
        assertEquals(1, after.size)
        assertEquals("b", after.single().key)
        assertEquals(3, after.single().items.size)
        assertEquals("b-名", after.single().name)
    }

    /** AC3：拆开之后合并前的所有原始名称都还在。因为合并从没改过 parcel 行，这就是同一份数据。 */
    @Test
    fun `拆开等于删登记 - 分组完整还原`() {
        val parcels = listOf(parcel(1, "a", "XX便利店"), parcel(3, "b", "菜鸟驿站XX店"))
        val aliases = mapOf("a" to alias("a", "b"))
        val merged = groupByPoint(parcels, t0, aliases = aliases)
        val split = groupByPoint(parcels, t0, aliases = emptyMap())
        assertEquals(1, merged.size)
        assertEquals(listOf("a", "b"), split.map { it.key }.sorted())
        assertEquals(listOf("XX便利店", "菜鸟驿站XX店"), split.map { it.name }.sorted())
    }

    @Test
    fun `合并后新到的旧写法短信自动落进同一个点`() {
        val aliases = mapOf("a" to alias("a", "b"))
        // 入库时 pointKey 仍是解析器给的 "a"，靠读取侧解析归位
        val groups = groupByPoint(listOf(parcel(9, "a", "XX便利店")), t0, aliases = aliases)
        assertEquals("b", groups.single().key)
    }

    // ---------- 预览 ----------

    @Test
    fun `预览给出两边件数、合计与原始名称样本`() {
        val source = PickupPointGroup(
            key = "a", name = "XX便利店", kind = PointKind.STATION, closesAtMinuteOfDay = 21 * 60,
            items = listOf(
                parcel(1, "a", "XX便利店", at = t0),
                parcel(2, "a", "xx 便利店", at = t0 + 1),
                parcel(3, "a", "XX便利店", at = t0 + 2),
            ),
            worst = Urgency.SAFE,
        )
        val target = group("b", "菜鸟驿站XX店", "菜鸟驿站（XX店）", "菜鸟驿站XX店", "菜鸟驿站XX店", "菜鸟驿站XX店")
        val preview = mergePreview(source, target)
        assertEquals(3, preview.source.count)
        assertEquals(5, preview.target.count)
        assertEquals(8, preview.total)
        // 去重、按时间新到旧
        assertEquals(listOf("XX便利店", "xx 便利店"), preview.source.nameSamples)
        assertEquals(2, preview.target.nameSamples.size)
        assertEquals("菜鸟驿站XX店", preview.target.nameSamples.first())
    }

    @Test
    fun `样本最多三个`() {
        val g = PickupPointGroup(
            key = "a", name = "a", kind = PointKind.STATION, closesAtMinuteOfDay = null,
            items = (1..6).map { parcel(it.toLong(), "a", "写法$it", at = t0 + it) }, worst = Urgency.SAFE,
        )
        assertEquals(3, nameSamplesOf(g.items).size)
    }

    // ---------- 疑似重复轻提示 ----------

    @Test
    fun `字面重叠足够长才提示，且绝不自动合并`() {
        val groups = listOf(group("cndm_a", "菜鸟驿站(XX小区店)"), group("cndm_b", "XX小区菜鸟驿站"))
        val suspects = suspectDuplicates(groups)
        assertEquals(1, suspects.size)
        // 提示不改变任何分组
        assertEquals(2, groupByPoint(listOf(parcel(1, "cndm_a", "菜鸟驿站(XX小区店)"), parcel(2, "cndm_b", "XX小区菜鸟驿站")), t0).size)
    }

    @Test
    fun `没有字面重叠时宁可漏报 - 便利店与菜鸟驿站提不出来`() {
        val groups = listOf(group("a", "XX便利店"), group("b", "菜鸟驿站(XX小区店)"))
        assertTrue(suspectDuplicates(groups).isEmpty())
    }

    @Test
    fun `不同点类型不提示`() {
        val groups = listOf(
            group("fc3", "丰巢柜·3号楼东门"),
            PickupPointGroup("cndm", "丰巢柜·3号楼东门站", PointKind.LOCKER, null, listOf(parcel(1, "cndm", "丰巢柜·3号楼东门站")), Urgency.SAFE),
        )
        assertTrue(suspectDuplicates(groups).isEmpty())
    }

    @Test
    fun `说过不是同一个点之后就闭嘴`() {
        val groups = listOf(group("a", "XX小区菜鸟驿站"), group("b", "XX小区菜鸟驿站2号门"))
        val pair = suspectDuplicates(groups).single()
        assertEquals(0, suspectDuplicates(groups, dismissed = setOf(pair.dismissKey)).size)
        // 定序无关：谁在前都记同一把键
        assertEquals(pair.dismissKey, pairKey(groups[1].key, groups[0].key))
    }

    @Test
    fun `公共片段长度`() {
        assertEquals(2, longestCommonSubstring("abcd", "xxcd"))
        assertEquals(0, longestCommonSubstring("", "abc"))
        assertEquals(4, longestCommonSubstring("小区菜鸟驿站", "菜鸟驿站"))
    }

    // ---------- 角标归属 ----------

    @Test
    fun `角标只归通用组件，与添加顺序无关`() {
        assertEquals(3, badgeOwnerId(listOf(7, 3, 5), boundWidgetIds = setOf(7, 5)))
        assertEquals(5, badgeOwnerId(listOf(7, 5), boundWidgetIds = setOf(7)))
    }

    @Test
    fun `只有绑定组件时由最小 id 顶上`() {
        assertEquals(4, badgeOwnerId(listOf(9, 4), boundWidgetIds = setOf(9, 4)))
    }

    @Test
    fun `没有组件时没有角标`() {
        assertNull(badgeOwnerId(emptyList(), emptySet()))
    }
}
