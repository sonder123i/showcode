package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * story-5 AC2：多项同时出问题时横条只显示最严重的一项加「还有 N 项」。
 * 所以严重度顺序本身就是产品决策，必须被测试钉住。
 */
class HealthTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `全正常时没有失效项 - 横条完全不出现`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true, lastSyncAt = now - HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
        assertNull(report.worst)
        assertTrue(report.allGood)
    }

    @Test
    fun `短信被拒是最严重项`() {
        val report = healthReport(
            smsGranted = false, notificationEnabled = false, lastSyncAt = null, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertEquals(HealthIssue.SMS_DENIED, report.worst)
        assertEquals(1, report.restCount)
    }

    @Test
    fun `只缺通知监听时它就是最严重项`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = false, lastSyncAt = now, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertEquals(HealthIssue.NOTIFICATION_DENIED, report.worst)
        assertEquals(0, report.restCount)
    }

    @Test
    fun `权限都正常但六小时没同步 - 怀疑后台被限制`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true,
            lastSyncAt = now - BACKGROUND_SUSPECT_MS - 1, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertEquals(listOf(HealthIssue.BACKGROUND_SUSPECTED), report.issues)
    }

    @Test
    fun `权限没开时不重复报后台问题 - 原因已经更清楚了`() {
        val report = healthReport(
            smsGranted = false, notificationEnabled = true,
            lastSyncAt = now - 24 * HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertEquals(listOf(HealthIssue.SMS_DENIED), report.issues)
    }

    @Test
    fun `刚同步过就不怀疑后台`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true,
            lastSyncAt = now - BACKGROUND_SUSPECT_MS + HOUR_MS, now = now, isXiaomi = false, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `没件可取时不报后台被限制 - 不然闲一天就常驻红条`() {
        // lastSyncAt 只在真的收到消息时盖章。「今天没买东西」也会让它超时——
        // 真机上闲 7 小时就挂着一条「后台可能被系统限制了」，那是误报。
        // 一条常驻的误报会把真正要看的两条（短信权限、通知权限）一起废掉。
        val report = healthReport(
            smsGranted = true, notificationEnabled = true,
            lastSyncAt = now - BACKGROUND_SUSPECT_MS - 1, now = now, isXiaomi = false, hasUnpicked = false,
        )
        assertTrue("没件可取时不该报任何失效项，实际 ${report.issues}", report.issues.isEmpty())
    }

    @Test
    fun `小米项只进体检表不进横条 - 它无法程序化判定 常驻报警会变成狼来了`() {
        val report = healthReport(
            smsGranted = true, notificationEnabled = true, lastSyncAt = now, now = now, isXiaomi = true, hasUnpicked = true,
        )
        assertTrue(report.issues.isEmpty())
        assertNull(report.worst)
        assertEquals(listOf(HealthIssue.MIUI_SMS_CLASS), report.manualChecks)
        // 体检表不全是绿的：有一项需要人工确认
        assertEquals(false, report.allGood)
    }

    @Test
    fun `没给短信权限时不提示小米通知类短信 - 先解决主通道`() {
        val report = healthReport(
            smsGranted = false, notificationEnabled = false, lastSyncAt = null, now = now, isXiaomi = true, hasUnpicked = true,
        )
        assertTrue(report.manualChecks.isEmpty())
    }
}
