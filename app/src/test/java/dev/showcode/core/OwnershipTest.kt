package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 转发来源 → 归属。这条规则唯一自动写 owner 的地方，判错代价是一个标签可改，
 * 所以只认两个真正会带正文进来的转发应用，其余一律算自己的件。
 */
class OwnershipTest {

    @Test
    fun `微信与 QQ 转发进来的算代取件`() {
        assertEquals(Owner.FAMILY, ownerOfForward("com.tencent.mm"))
        assertEquals(Owner.FAMILY, ownerOfForward("com.tencent.mobileqq"))
    }

    @Test
    fun `驿站自家 App 的通知算自己的件`() {
        assertEquals(Owner.SELF, ownerOfForward("com.cainiao.wireless"))
        assertEquals(Owner.SELF, ownerOfForward("com.fcbox.hiveconsumer"))
    }

    @Test
    fun `来源未知时保守地算自己的件`() {
        assertEquals(Owner.SELF, ownerOfForward(null))
        assertEquals(Owner.SELF, ownerOfForward(""))
    }
}
