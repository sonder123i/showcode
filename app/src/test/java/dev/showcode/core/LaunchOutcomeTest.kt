package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「打开官方 App」的结局判据。
 *
 * 这一格的唯一存在理由是竞品 issue #39：用户点了没反应，报的却是「不能添加身份码」——
 * 因为**静默的失败不会告诉人下一步**。所以四档必须各答一件事，尤其不能把
 * 「装了但被 ROM 拦住」和「没装」并成一档：把前者说成后者，人会去重装一个已经装好的 App。
 */
class LaunchOutcomeTest {

    private fun outcome(hasPackage: Boolean, resolvable: Boolean, started: Boolean) =
        launchOutcome(LaunchFacts(hasPackage, resolvable, started))

    @Test
    fun `真的起来了就没有第二句可说`() {
        assertEquals(LaunchOutcome.Opened, outcome(true, true, true))
    }

    @Test
    fun `没选过应用时不许说没装`() {
        // 这张码根本没关联 App。说「这台手机上没装」是把它没记过这件事推给装机状态。
        assertEquals(LaunchOutcome.NoTarget, outcome(false, false, false))
    }

    @Test
    fun `解不出启动入口就是没装`() {
        assertEquals(LaunchOutcome.NotInstalled, outcome(true, false, false))
    }

    @Test
    fun `解得出却没起来是拦住，不是没装`() {
        // 这一档的答案是 ROM 里的一个开关。并到 NotInstalled 那一档，用户会去重装。
        assertEquals(LaunchOutcome.Blocked, outcome(true, true, false))
    }

    @Test
    fun `没有入口却报告成功是不可能的组合，仍按没装答`() {
        // 取数层真出这种结果时不许静默变成「打开好了」。
        assertEquals(LaunchOutcome.NotInstalled, outcome(true, false, true))
    }
}
