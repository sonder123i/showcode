package dev.showcode.core

import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.UnrecognizedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「按 App 关通知」的判据。
 *
 * 这一层的全部风险是**误挡**：漏收一条只是少一张卡，误挡一条是他站在柜机前才发现的。
 * 所以下面每一例都在问「这次到底该不该挡」，而不是问「开关能不能切」。
 */
class NotifyAppsTest {

    private fun notification(body: String, pkg: String?, sender: String = "菜鸟驿站") = IncomingMessage(
        sender = sender,
        body = body,
        receivedAt = 1_700_000_000_000L,
        channel = Source.NOTIFICATION,
        sourcePackage = pkg,
    )

    private val pickupBody = "【菜鸟驿站】您的包裹已到向阳小区菜鸟驿站，取件码5-2-7712"

    @Test
    fun `关掉一个 App 只挡通知，不挡它发的短信`() {
        // 两套语义各管各的：短信走发件人号码那套管。搅在一起的后果是「关掉菜鸟 App」
        // 连带让菜鸟的短信静默，而那不是任何人的意图，症状还查不出来。
        val disabled = setOf("com.cainiao.wireless")
        assertFalse(
            NotifyApps.admits(
                notification(pickupBody, "com.cainiao.wireless"),
                disabled,
            ),
        )
        assertTrue(
            NotifyApps.admits(
                IncomingMessage(
                    sender = "1069",
                    body = pickupBody,
                    receivedAt = 1_700_000_000_000L,
                    channel = Source.SMS,
                ),
                disabled,
            ),
        )
    }

    @Test
    fun `判据认包名不认显示名`() {
        // sender 是 PackageManager 给的显示名，会改、会重名、会随语言变。
        // 拿它当判据，用户关掉「菜鸟驿站」时，真正被静音的是另一个也叫这个名字的 App。
        val disabled = setOf("com.cainiao.wireless")
        val sameLabelOtherPkg = notification(pickupBody, "com.other.app", sender = "菜鸟驿站")
        assertTrue(NotifyApps.admits(sameLabelOtherPkg, disabled))
    }

    @Test
    fun `认不出包名的通知照收`() {
        // 系统级那一类通知拿不到包名。宁可多收一条噪音，也不要误挡一个真码。
        assertTrue(NotifyApps.admits(notification(pickupBody, null), setOf("com.cainiao.wireless")))
        assertTrue(NotifyApps.admits(notification(pickupBody, "   "), setOf("com.cainiao.wireless")))
    }

    @Test
    fun `同一个包名的大小写不算两个来源`() {
        // 大小写与空白都算同一个来源，否则用户要关两次才算关掉。
        assertEquals("com.cainiao.wireless", NotifyApps.normalizePackage("  com.Cainiao.Wireless "))
        assertFalse(NotifyApps.admits(notification(pickupBody, "com.CAINIAO.wireless"), setOf("com.cainiao.wireless")))
        assertNull(NotifyApps.normalizePackage(""))
    }

    @Test
    fun `开关列表把推得最多的排前面`() {
        val apps = listOf(
            NotifyApp("a", "A", false, lastSeenAt = 100L, seenCount = 3),
            NotifyApp("b", "B", false, lastSeenAt = 900L, seenCount = 1),
            NotifyApp("c", "C", true, lastSeenAt = 50L, seenCount = 3),
        )
        assertEquals(listOf("a", "c", "b"), NotifyApps.sortedForSwitches(apps).map { it.packageName })
    }

    @Test
    fun `挡掉的消息一条都不写，也不记账`() {
        // 记账会把反悔的路堵死：重新打开那个 App 之后，账本里那行会让它再也进不来。
        val plan = planIngest(
            message = notification(pickupBody, "com.cainiao.wireless"),
            outcome = ParseOutcome.Ignored,
            fingerprint = "fp",
            appMuted = true,
        )
        assertEquals(MessageSkip.NOTIFY_APP_OFF, plan.skip)
        assertTrue(plan.parcels.isEmpty())
        assertNull(plan.unrecognized)
        assertFalse(plan.worthLedgering)
    }

    @Test
    fun `挡掉的那条即使是能认出来的件也一个字不写`() {
        // 开关的含义是「这个来源我以后都不要」，不是「这个来源送去未识别」。
        // 落未识别会把那条承诺翻成「关掉了还看得见」，而那个列表本来就是失败显性化用的。
        val plan = planIngest(
            message = notification(pickupBody, "com.cainiao.wireless"),
            outcome = ParseOutcome.Rejected(UnrecognizedReason.NO_CODE_FOUND),
            fingerprint = "fp",
            appMuted = true,
        )
        assertNull(plan.unrecognized)
    }

    @Test
    fun `来源级配置先于发件人级表态`() {
        // 两条同时成立时先报「这个 App 被关掉了」：那个是用户更近期、更主动的决定，
        // 而收容页的「恢复」只该指向一条他本来打算看的消息。
        val plan = planIngest(
            message = notification(pickupBody, "com.cainiao.wireless"),
            outcome = ParseOutcome.Ignored,
            fingerprint = "fp",
            senderMuted = true,
            appMuted = true,
        )
        assertEquals(MessageSkip.NOTIFY_APP_OFF, plan.skip)
    }

    @Test
    fun `账本命中时仍然先报账本`() {
        // 同一条重放永远排第一，否则一条被重放的消息会伪装成「来源被关」。
        val plan = planIngest(
            message = notification(pickupBody, "com.cainiao.wireless"),
            outcome = ParseOutcome.Ignored,
            fingerprint = "fp",
            alreadySeen = true,
            appMuted = true,
        )
        assertEquals(MessageSkip.ALREADY_SEEN, plan.skip)
    }

    @Test
    fun `没关任何东西时一切照旧`() {
        assertTrue(NotifyApps.admits(notification(pickupBody, "com.cainiao.wireless"), emptySet()))
        val plan = planIngest(
            message = notification(pickupBody, "com.cainiao.wireless"),
            outcome = ParseOutcome.Parsed(listOf(sampleParsed())),
            fingerprint = "fp",
            appMuted = false,
        )
        assertEquals(MessageSkip.NONE, plan.skip)
        assertTrue(plan.worthLedgering)
    }

    private fun sampleParsed() = dev.showcode.core.parse.ParsedParcel(
        code = "5-2-7712",
        lockerNo = null,
        carrier = "中通快递",
        pointName = "向阳小区菜鸟驿站",
        pointKind = PointKind.STATION,
        closesAtMinuteOfDay = 1260,
        freeUntilAt = null,
    )
}
