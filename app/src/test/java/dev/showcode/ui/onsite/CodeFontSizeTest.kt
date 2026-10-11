package dev.showcode.ui.onsite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取件码字号的防溢出不变量（mockup 的 Check finding M-r2-3：码宽必须 ≤ 可用宽度）。
 * 这个测试存在的意义是：字号规则一旦被改坏，现场模式就会在 320dp 的窄屏上把码挤成两行，
 * 而那种 bug 在开发用的大屏模拟器上永远看不见。
 */
class CodeFontSizeTest {

    /** 等宽字体每字符约 0.6em，再加 0.02em 字距。 */
    private val widthPerChar = 0.62f

    /** OnsiteCodeBody 的两侧内边距各 20dp。 */
    private val horizontalPadding = 40f

    @Test
    fun `与 H5 参照的 base 分档逐点对齐`() {
        // base: len > 8 → 54 · len > 6 → 64 · 其余 72
        assertEquals(72f, codeFontSizeSp("8-3-10", true, 390f, 1f), 0.01f)
        assertEquals(64f, codeFontSizeSp("8-3-102", true, 390f, 1f), 0.01f)
        assertEquals(54f, codeFontSizeSp("JD7739201", true, 800f, 1f), 0.01f)
    }

    @Test
    fun `窄屏 320dp 时 vw 上限生效 - 8 字符码缩到 56`() {
        // mockup 注释里的同一个数字：8 字符在 320 视口下 cap = 140/8 vw = 56
        assertEquals(56f, codeFontSizeSp("8-3-1024", true, 320f, 1f), 0.01f)
    }

    @Test
    fun `宽屏时走 base 上限而不是 vw 上限`() {
        assertEquals(64f, codeFontSizeSp("8-3-1024", true, 390f, 1f), 0.01f)
    }

    @Test
    fun `无柜号时取件码放大 15% - 不是留白是重排`() {
        assertEquals(54f, codeFontSizeSp("JD7739201", true, 800f, 1f), 0.01f)
        assertEquals(62f, codeFontSizeSp("JD7739201", false, 800f, 1f), 0.01f)
    }

    @Test
    fun `不变量 - 任何码长、屏宽、系统字体缩放下码宽都不超过可用宽度`() {
        val viewports = listOf(320f, 360f, 390f, 412f, 600f)
        val fontScales = listOf(0.85f, 1f, 1.15f, 1.3f, 2f)
        for (len in 1..14) {
            val code = "A".repeat(len)
            for (viewport in viewports) {
                for (scale in fontScales) {
                    for (hasLocker in listOf(true, false)) {
                        val sp = codeFontSizeSp(code, hasLocker, viewport, scale)
                        // sp × fontScale 才是实际渲染的 dp 尺寸
                        val renderedWidth = sp * scale * widthPerChar * len
                        val available = viewport - horizontalPadding
                        assertTrue(
                            "len=$len viewport=$viewport fontScale=$scale hasLocker=$hasLocker " +
                                "→ 码宽 ${"%.1f".format(renderedWidth)}dp > 可用 ${"%.1f".format(available)}dp",
                            renderedWidth <= available,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `fontScale 为 1 时结果与 CSS 完全一致`() {
        // CSS 的 px 不跟随系统字体缩放；只有 fontScale = 1 时两者才该逐点相等
        assertEquals(56f, codeFontSizeSp("8-3-1024", true, 320f, 1f), 0.01f)
        assertEquals(72f, codeFontSizeSp("A12", true, 412f, 1f), 0.01f)
    }

    @Test
    fun `系统字体放大时 base 可以让步给可用宽度`() {
        val sp = codeFontSizeSp("8-3-1024", true, 320f, 1.3f)
        assertTrue(sp * 1.3f * widthPerChar * 8 <= 320f - horizontalPadding)
        // 放大后 sp 值变小，但渲染出来的 dp 仍被按在 56
        assertEquals(56f, sp * 1.3f, 0.01f)
    }
}
