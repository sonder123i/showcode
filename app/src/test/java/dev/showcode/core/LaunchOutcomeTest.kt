package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「打开官方 App」这条兜底链的判据。
 *
 * 竞品 issue #39 的标题是「不能添加淘宝和拼多多身份码」，正文只有一句「点击时无反应」，
 * 维护者的答案是一个系统开关。**静默的失败不会告诉人下一步**，所以这一格的每一档
 * 都必须对应一句不同的话——尤其不能把「被拦住」说成「没装」。
 */
class LaunchOutcomeTest {

    private fun outcome(hasPackage: Boolean, vararg attempts: Attempt) =
        launchOutcome(hasPackage, attempts.toList())

    @Test
    fun `任何一级真起来了就没有第二句可说`() {
        assertEquals(LaunchOutcome.Opened, outcome(true, Attempt.NO_HANDLER, Attempt.OPENED))
        assertEquals(LaunchOutcome.Opened, outcome(true, Attempt.OPENED))
    }

    @Test
    fun `没选过应用时不许说没装`() {
        // 这张码根本没关联 App。说「这台手机上没装」是把它没记过这件事推给装机状态。
        assertEquals(LaunchOutcome.NoTarget, outcome(false))
        assertEquals(LaunchOutcome.NoTarget, outcome(false, Attempt.NO_HANDLER, Attempt.NO_HANDLER))
    }

    @Test
    fun `每一级都没有处理器才是没装`() {
        assertEquals(LaunchOutcome.NotInstalled, outcome(true, Attempt.NO_HANDLER, Attempt.NO_HANDLER))
    }

    @Test
    fun `掺进一次被拦住就不是没装`() {
        // 这是整条判据的意义所在：说成没装，用户会去重装一个已经装好的 App，
        // 而真正该做的是去系统里那个开关。
        assertEquals(LaunchOutcome.Blocked, outcome(true, Attempt.NO_HANDLER, Attempt.REFUSED))
        assertEquals(LaunchOutcome.Blocked, outcome(true, Attempt.REFUSED))
    }

    @Test
    fun `一级都没试过不许当成打开成功`() {
        // 取数层真出这种结果时，宁可报「拦住」也不静默——静默就是 issue #39。
        assertEquals(LaunchOutcome.Blocked, outcome(true))
    }

    // ---------- 兜底链本身 ----------

    @Test
    fun `不认识的那家只走包名启动那一级`() {
        assertEquals(emptyList<String>(), DeepLinkLadder.deepLinksFor("com.someone.else"))
        assertEquals(null, DeepLinkLadder.webFallbackFor("com.someone.else"))
    }

    @Test
    fun `直达链接的包名大小写不算两家`() {
        assertEquals(
            DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO),
            DeepLinkLadder.deepLinksFor("  COM.Taobao.Taobao "),
        )
    }

    @Test
    fun `h5Url 必须整段百分号编码，否则会被对方截断`() {
        val link = DeepLinkLadder.deepLinksFor(DeepLinkLadder.TAOBAO).single()
        val query = link.substringAfter("h5Url=")
        assertEquals("裸 : 会让淘宝把参数截成 https:", true, ':' !in query)
        assertEquals("裸 / 同上", true, '/' !in query)
        assertEquals("https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1100333%2Flast-mile-fe%2Fm-end-school-tab%2Fhome", query)
    }

    @Test
    fun `拼多多那家没有网页退路，只有它那一条 scheme`() {
        val links = DeepLinkLadder.deepLinksFor(DeepLinkLadder.PINDUODUO)
        assertEquals(1, links.size)
        assertEquals(true, links.single().startsWith("pinduoduo://"))
        assertEquals(null, DeepLinkLadder.webFallbackFor(DeepLinkLadder.PINDUODUO))
    }

    @Test
    fun `快捷项里那两家都在链上有说法`() {
        // 预置了却没有任何一级的包名是空壳——它会在选择器里显示成一颗点了只会失败的按钮。
        DeepLinkLadder.knownPackages.forEach { pkg ->
            val covered = DeepLinkLadder.deepLinksFor(pkg).isNotEmpty() ||
                DeepLinkLadder.webFallbackFor(pkg) != null
            assertEquals("$pkg 应该在兜底链上有东西", true, covered)
        }
    }
}
