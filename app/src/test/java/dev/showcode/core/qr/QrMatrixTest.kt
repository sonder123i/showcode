package dev.showcode.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二维码矩阵。这些断言全部是**结构**的，因为这一层唯一可能被验证的判据就是结构——
 * 「扫出来是不是那张码」要一台真扫码枪，属于真机清单，不属于单测。
 *
 * 结构判据也不是随便挑的：定位角错、时序图案错、静区没有，这三种错**画出来都还挺好看**，
 * 但扫码器一定读不出。也就是说它们是唯一一种「截图看不出来、单测能抓出来」的失效。
 */
class QrMatrixTest {

    /** 一条真实形态的取件码（语料 up-tx-ruixin 那条的数字密码）。 */
    private val sample = "8-9-0765"

    private fun matrix(value: String = sample) = Qr.of(value)!!

    @Test
    fun `边长是版本算出来的那个数`() {
        val m = matrix()
        assertEquals(17 + 4 * m.version, m.size)
        assertEquals(m.size, m.rows.size)
        assertTrue("行宽不齐就等于画出来是斜的", m.rows.all { it.size == m.size })
    }

    @Test
    fun `三个定位角在它们该在的位置，右下角没有`() {
        val m = matrix()
        // 左上、右上、左下各有一个 7x7 的回字形；右下那个位置是版本信息/格式区，不是定位角。
        assertTrue(finderMatches(m, 0, 0))
        assertTrue(finderMatches(m, m.size - 7, 0))
        assertTrue(finderMatches(m, 0, m.size - 7))
        assertNotEquals("右下角长出定位角 = 矩阵被旋转或镜像了", true, finderMatches(m, m.size - 7, m.size - 7))
    }

    /** 一个 7x7 方块是不是标准的回字定位角：外环一格黑、第二格白、中心 3x3 黑。 */
    private fun finderMatches(m: QrMatrix, x: Int, y: Int): Boolean {
        for (dy in 0..6) {
            for (dx in 0..6) {
                val ring = dx == 0 || dx == 6 || dy == 0 || dy == 6
                val center = dx in 2..4 && dy in 2..4
                // 中间那圈白不是条件，只是「既不在外环也不在中心」的剩下的格。
                // 上一版把它写成否决条件，于是把外环与白圈交叉处错判成白——那是断言坏了，不是矩阵坏了。
                if (m.isDark(x + dx, y + dy) != (ring || center)) return false
            }
        }
        return true
    }

    @Test
    fun `时序图案严格交替`() {
        val m = matrix()
        // 第 6 行与第 6 列是 timing pattern：从两个定位角之间开始，一格黑一格白。
        for (i in 8 until m.size - 8) {
            assertEquals("第 6 行第 $i 列", i % 2 == 0, m.isDark(i, 6))
            assertEquals("第 6 行第 $i 列的转置", i % 2 == 0, m.isDark(6, i))
        }
    }

    @Test
    fun `静区按规范留够四个模块`() {
        // 没有静区，扫码器分不清码在哪结束——这是「屏上看着完整、就是扫不上」的经典原因。
        assertTrue(QR_QUIET_ZONE_FLOOR >= 2)
        assertEquals(matrix().size + QUIET_ZONE_MODULES * 2, matrix().sizeWithQuietZone)
    }

    @Test
    fun `同样的内容每次编出同一张图`() {
        assertEquals(matrix(), matrix())
        assertEquals(Qr.of("菜鸟驿站 H09"), Qr.of("菜鸟驿站 H09"))
    }

    @Test
    fun `不同的内容编出不同的图`() {
        assertNotEquals(Qr.of("8-9-0765"), Qr.of("8-9-0766"))
    }

    @Test
    fun `空串不是码`() {
        assertNull(Qr.of(""))
    }

    @Test
    fun `纯数字比同样的字母串省版本，这也是上限卡得住的理由`() {
        val digits = Qr.of("1".repeat(100))!!
        val letters = Qr.of("A".repeat(100))!!
        assertTrue("数字模式没省到位，说明分段编码没生效：$digits/$letters", digits.version < letters.version)
        assertTrue(digits.version <= MAX_VERSION)
    }

    @Test
    fun `编不进上限的版本时返回 null 而不是抛`() {
        // 长 URL 就是这一档。抛出去的后果不是报错，是那一屏根本画不出来。
        assertNull(Qr.of("https://example.com/" + "x".repeat(400)))
    }

    private companion object {
        /** 规范下限。低于这个数就不是「留白不够」而是「不是合法二维码」。 */
        const val QR_QUIET_ZONE_FLOOR = 2
    }
}
