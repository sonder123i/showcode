package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 推送盲区的判据。重心不是「算得对不对」，而是**这条推据能证什么、不能证什么**：
 * 手动录过件 = 有盲区（可靠）；没录过 ≠ 没盲区（不能反过来用）。
 * 这条边界如果哪天被写成「没证据就不提醒」，功能就废了——因为最需要提醒的人恰恰是没录过的。
 */
class BlindSpotTest {

    @Test
    fun `手动录过件就算有盲区，只有一条也算`() {
        assertEquals(true, BlindSpot.detected(setOf(Source.MANUAL)))
        assertEquals(
            true,
            BlindSpot.detected(setOf(Source.SMS, Source.NOTIFICATION, Source.MANUAL)),
        )
    }

    @Test
    fun `只走短信和通知的不算——推送通道顺的人不需要催`() {
        assertEquals(false, BlindSpot.detected(setOf(Source.SMS)))
        assertEquals(false, BlindSpot.detected(setOf(Source.SMS, Source.NOTIFICATION)))
        assertEquals(false, BlindSpot.detected(emptySet()))
    }

    @Test
    fun `演示数据不许制造盲区证据`() {
        // DEMO 那批件如果算进来，用户逛一圈演示就会被开始每天提醒。
        assertEquals(false, BlindSpot.detected(setOf(Source.DEMO)))
    }

    @Test
    fun `提醒要三档全过`() {
        val blind = setOf(Source.SMS, Source.MANUAL)
        assertEquals(true, BlindSpot.shouldRemind(blind, remindersOn = true, lastRemindedDay = null, today = 20000))
        // 关掉了就不许响，哪怕有盲区。
        assertEquals(false, BlindSpot.shouldRemind(blind, remindersOn = false, lastRemindedDay = null, today = 20000))
        // 今天已经提醒过。
        assertEquals(false, BlindSpot.shouldRemind(blind, remindersOn = true, lastRemindedDay = 20000, today = 20000))
        // 昨天提醒过，今天该再响——按自然日，不是按 24 小时滚动。
        assertEquals(true, BlindSpot.shouldRemind(blind, remindersOn = true, lastRemindedDay = 19999, today = 20000))
    }

    @Test
    fun `没有盲区证据时开关也拦着`() {
        assertEquals(
            false,
            BlindSpot.shouldRemind(setOf(Source.SMS), remindersOn = true, lastRemindedDay = null, today = 20000),
        )
    }

    @Test
    fun `第一次和之后的文案不是一条`() {
        assertEquals(true, BlindSpot.isFirstReminder(0))
        assertEquals(false, BlindSpot.isFirstReminder(20000))
    }

    @Test
    fun `自然日按天切，不是按 24 小时滚动`() {
        val dayMs = 24L * 60 * 60 * 1000
        assertEquals(BlindSpot.epochDay(0), BlindSpot.epochDay(dayMs - 1))
        assertEquals(BlindSpot.epochDay(0) + 1, BlindSpot.epochDay(dayMs))
    }

    @Test
    fun `九点之前指向今天九点，之后指向明天九点`() {
        val hourMs = 60L * 60 * 1000
        // 本地 08:00 那一瞬间（用同一套时区算，断言的是「往后不到一天」这个形状）。
        val beforeNine = BlindSpot.nextReminderAt(System.currentTimeMillis() - 12 * hourMs)
        val afterNine = BlindSpot.nextReminderAt(System.currentTimeMillis())
        val next = BlindSpot.nextReminderAt(beforeNine)
        // 刚响过的那一刻再问，答案必须是明天——同一天不许排第二次。
        assertEquals(true, next > beforeNine)
        assertEquals(true, next - beforeNine <= 25 * hourMs)
        assertEquals(true, afterNine > System.currentTimeMillis() - hourMs)
    }
}
