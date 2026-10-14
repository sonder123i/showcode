package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 身份码的判据。重心只有一件事：**装不下、不能扫的那些，必须在保存之前就说**，
 * 因为这一屏的失效现场是「人站在机器前，屏上那张码看起来完全正常」。
 */
class IdentityCodeTest {

    private fun validate(label: String, value: String, count: Int = 0, dynamic: Boolean? = false) =
        IdentityCodes.validate(label, value, dynamic, count)

    @Test
    fun `一条正常的码过得去`() {
        assertNull(validate("菜鸟驿站会员码", "8-9-0765"))
        assertNull(validate("  丰巢寄件码  ", "  https://fcbox.com/x  "))
    }

    @Test
    fun `名称和码值都不能空`() {
        assertEquals(IdentityReject.EMPTY_LABEL, validate("", "8877"))
        assertEquals(IdentityReject.EMPTY_LABEL, validate("   ", "8877"))
        assertEquals(IdentityReject.EMPTY_VALUE, validate("会员码", "   "))
    }

    @Test
    fun `名称是标题所以有上限`() {
        assertEquals(IdentityReject.LABEL_TOO_LONG, validate("名".repeat(IdentityCodes.MAX_LABEL_LENGTH + 1), "8877"))
    }

    @Test
    fun `编不成码的长串在保存前就被拒`() {
        // 上限不是这里写死的数字，是「真的编一遍」的结果——见 IdentityCodes.validate 的注释。
        assertEquals(IdentityReject.VALUE_NOT_ENCODABLE, validate("会员码", "https://example.com/" + "x".repeat(400)))
    }

    @Test
    fun `中文也能编进去，所以不会被长度判据误杀`() {
        // 驿站那类码里出现门店中文名是常态。字节模式比数字模式贵得多，但它仍然编得下。
        assertNull(validate("驿站", "向阳小区菜鸟驿站会员码"))
    }

    @Test
    fun `条数到上限时不再收`() {
        assertEquals(
            IdentityReject.TOO_MANY,
            validate("会员码", "8877", IdentityCodes.MAX_CODES),
        )
    }

    @Test
    fun `先报名称再报码值，因为那是用户先看的那一格`() {
        assertEquals(IdentityReject.EMPTY_LABEL, validate("", "   "))
    }

    @Test
    fun `会不会变那一格没答就先不过，别的都填全也一样`() {
        // 猜成静态 = 他举着一张已经作废的码站在机器前；猜成动态 = 屏上多一句提醒。
        // 代价不对称，所以这一格不给默认值。
        assertEquals(IdentityReject.NEEDS_DYNAMIC, validate("会员码", "8-9-0765", dynamic = null))
    }

    @Test
    fun `会变的那张才需要那句不吉利的话`() {
        assertTrue(IdentityCodes.shouldWarnStale(code(dynamic = true)))
        assertFalse(IdentityCodes.shouldWarnStale(code(dynamic = false)))
    }

    @Test
    fun `折叠只在有退路时才允许`() {
        // 没有选过官方 App 就把码藏起来，等于把唯一剩下的信息也拿走——那是做成死路，不是省事。
        val withWayOut = IdentityCode(1, "会员码", "8877", dynamic = true, appPackage = "com.x.unmeng")
        val withoutWayOut = withWayOut.copy(appPackage = null)
        assertTrue(IdentityCodes.shouldCollapseQr(withWayOut, revealed = false))
        assertFalse(IdentityCodes.shouldCollapseQr(withoutWayOut, revealed = false))
        assertFalse(IdentityCodes.shouldCollapseQr(withWayOut, revealed = true))
        // 静态码没有任何理由折叠：它不会作废。
        assertFalse(IdentityCodes.shouldCollapseQr(withWayOut.copy(dynamic = false), revealed = false))
    }

    @Test
    fun `亮度是抬地板，不是拉满，也不许把人调暗`() {
        // 夜里在楼下取件时 1.0 是拿屏幕照人眼；而用户自己开到最亮的人不该被我们降下来。
        assertEquals(IdentityCodes.BRIGHTNESS_FLOOR, IdentityCodes.liftedBrightness(null))
        assertEquals(IdentityCodes.BRIGHTNESS_FLOOR, IdentityCodes.liftedBrightness(-1f))
        assertEquals(IdentityCodes.BRIGHTNESS_FLOOR, IdentityCodes.liftedBrightness(0.3f))
        assertEquals(0.95f, IdentityCodes.liftedBrightness(0.95f), 0f)
    }

    private fun code(dynamic: Boolean) = IdentityCode(
        id = 1,
        label = "菜鸟驿站会员码",
        value = "8-9-0765",
        dynamic = dynamic,
    )
}
