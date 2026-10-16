package dev.showcode.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中性阶的相守卫。
 *
 * 判据不是「必须纯灰」，而是「不许出现紫/品红相」：
 *   合法 —— R ≤ G ≤ B（纯灰取等号，教务那套冷调族是严格小于）
 *   违规 —— R > G（M3 基线 tone 面板的共同特征）
 *
 * 起因是 2026-10-13 那次修复：设置页滑杆的未填充段实测采样到 #E8DEF8，
 * 正是 M3 secondaryContainer 的默认值。那次把容器 role 全压成中性灰顺手压平了层次，
 * 所以这次重新分档、同时用断言把相守卫钉住——**分档可以，跑偏不行**。
 */
class NeutralRampTest {

    private fun rgb(r: Int, g: Int, b: Int) = Color(r / 255f, g / 255f, b / 255f)

    private fun channels(c: Color) = Triple((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    private fun luminance(c: Color): Double {
        fun f(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * f(c.red) + 0.7152 * f(c.green) + 0.0722 * f(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `表面与描边全部落在冷调或纯灰上`() {
        val ramp = listOf(
            "Canvas" to Canvas,
            "SurfaceCard" to SurfaceCard,
            "SurfaceSunken" to SurfaceSunken,
            "SurfaceDeep" to SurfaceDeep,
            "SurfaceFloating" to SurfaceFloating,
            "Hairline" to Hairline,
            "HairlineStrong" to HairlineStrong,
            "Ink" to Ink,
            "InkSecondary" to InkSecondary,
            "InkMuted" to InkMuted,
            "InkFaint" to InkFaint,
            "OnsiteInk" to OnsiteInk,
            "OnsiteSurface" to OnsiteSurface,
        )
        ramp.forEach { (name, color) ->
            val (r, g, b) = channels(color)
            assertTrue(
                "$name 相偏了：rgb($r,$g,$b) 不满足 R ≤ G ≤ B。R > G 是 M3 紫/品红 tone 的特征，" +
                    "上一次它把 #E8DEF8 漏进了设置页滑杆。",
                r <= g && g <= b,
            )
        }
    }

    /** 判据本身得真的有效：这三个是 M3 基线 tone 面板的实际默认值，必须被拒。 */
    @Test
    fun `M3 的三个默认紫值确实会被这条判据拒掉`() {
        val m3Defaults = listOf(
            "secondaryContainer #E8DEF8" to rgb(0xE8, 0xDE, 0xF8),
            "surfaceContainer  #F3EDF7" to rgb(0xF3, 0xED, 0xF7),
            "surfaceVariant    #E7E0EC" to rgb(0xE7, 0xE0, 0xEC),
        )
        m3Defaults.forEach { (name, color) ->
            val (r, g, b) = channels(color)
            assertTrue("$name 应当被 R ≤ G ≤ B 拒掉，但它通过了", r > g)
            assertTrue("$name 的 B 应当高于 G（紫相）", b > g)
        }
    }

    /**
     * 亮度必须单调：页面 < 卡片 < 内嵌块 < 凹槽。
     * 「卡片浮不浮得起来」就靠这个间距，所以它是断言而不是观感。
     */
    @Test
    fun `表面阶严格单调变深`() {
        val ladder = listOf(Canvas, SurfaceCard, SurfaceSunken, SurfaceDeep)
        ladder.zipWithNext().forEach { (light, dark) ->
            assertTrue("亮度阶断了：$light 不比 $dark 亮", luminance(light) - luminance(dark) > 0.01)
        }
        assertTrue(
            "卡面与页面几乎同亮，卡片浮不起来",
            luminance(Canvas) - luminance(SurfaceCard) >= 0.02,
        )
    }

    /**
     * 次级文字必须过 AA。改之前首页最暗的一档手调 alpha 合成成约 2.5:1，
     * 而它承载的是柜号和「+3 件」这类真信息。
     *
     * ⚠️ 2026-10-16 起基准是**环境层最暗那一角**，不是纯白：管理模式的页面底已经是那层渐变，
     * 纯白只剩「M3 组件兜底」这一个身份。旧判据对着纯白算，于是 InkMuted #667085 看着是 4.97:1
     * 合格，真机上落在渐变最暗角却只有 3.80:1——判据挑错了对手，合格就是假的。
     */
    @Test
    fun `承载信息的墨阶过 AA，装饰档不用于信息`() {
        listOf(Ink, InkSecondary, InkMuted).forEach {
            assertTrue("墨阶 $it 对环境层最暗角低于 AA", contrast(it, EnvironmentDarkest) >= 4.5)
            assertTrue("墨阶 $it 对白底低于 AA", contrast(it, Canvas) >= 4.5)
        }
        // InkFaint 故意不过 AA：它只给不承载信息的字形（关闭用的 ✕、示例水印）。
        assertTrue("InkFaint 若过了 AA，就该改用它承载信息", contrast(InkFaint, Canvas) < 4.5)
    }

    /**
     * 品牌蓝的两档分工：填充档不做字、字档要能压在环境层最暗角上。
     * 这条断言的存在意义是挡住「顺手把 Accent 用回文字色」——那是这一轮刚结掉的一笔账。
     *
     * ⚠️ AccentInk 现在是教务的原值 `#0057D9`（`NavSelectedAccentLight`），它能过 AA
     * 靠的是环境层暗角 alpha 收到 0.09；谁把那个 alpha 加回去，这条会当场红。
     */
    @Test
    fun `品牌蓝的字档在环境层最暗角过 AA，填充档不许当字用`() {
        assertTrue("AccentInk 在环境层最暗角低于 AA", contrast(AccentInk, EnvironmentDarkest) >= 4.5)
        assertTrue(
            "Accent 竟然能在环境层最暗角当文字用了？那说明它已经深到不该再叫填充档",
            contrast(Accent, EnvironmentDarkest) < 3.0,
        )
    }

    /**
     * 卡面必须浮在环境层之上——判据的对手是**紧挨卡片的**那片页面，不是环境层的全局最亮。
     *
     * 全局最亮在顶部（0.18w, 0.12h 那道白光，L 0.9412），那一带是页眉与状态栏、没有卡片；
     * 拿它当对手会得出一条与实际观感无关的红线。真正的约束是左右各 3% 宽那条页面边条
     * （y 取 0.15h~0.85h），那里最亮 #F0F5F9（L 0.9067）。算它的脚本是 `.verify/env-check.py`。
     *
     * ⚠️ 若哪天有卡片挪进页眉那一带，这条判据就失效了（那里 ΔL 只剩 0.0148）——所以它同时是
     * 一条布局约束：**别把卡片放到顶部白光下面**。
     */
    @Test
    fun `卡面比环境层里卡片旁边那片页面亮`() {
        assertTrue(
            "卡面陷进页面里了：与卡边最亮处的亮度差不足",
            luminance(SurfaceCard) - luminance(EnvironmentCardEdgeLightest) >= 0.03,
        )
    }

    /** 语义浅底必须是同族色合成到白上的结果，不许随手取一个灰。 */
    @Test
    fun `状态浅底与实色同族`() {
        fun tintOf(base: Color, a: Double) = Color(
            (base.red * a + (1 - a)).toFloat(),
            (base.green * a + (1 - a)).toFloat(),
            (base.blue * a + (1 - a)).toFloat(),
        )
        listOf(
            "Safe" to (UrgencySafe to UrgencySafeTint),
            "DueSoon" to (UrgencyDueSoon to UrgencyDueSoonTint),
            "AtRisk" to (UrgencyAtRisk to UrgencyAtRiskTint),
            "Accent" to (Accent to AccentTint),
        ).forEach { (name, pair) ->
            val expected = tintOf(pair.first, 0.10)
            val actual = pair.second
            assertEquals("$name 浅底的 R 通道不是实色的 10% 合成", expected.red.toDouble(), actual.red.toDouble(), 0.012)
            assertEquals("$name 浅底的 G 通道不是实色的 10% 合成", expected.green.toDouble(), actual.green.toDouble(), 0.012)
            assertEquals("$name 浅底的 B 通道不是实色的 10% 合成", expected.blue.toDouble(), actual.blue.toDouble(), 0.012)
        }
    }
}
