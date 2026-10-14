package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剪贴板自动导入的判据。重心不是「能不能填」，而是**两条路的不对称**：
 * 自动填保守、显式粘贴照做。压成一套判据的话，要么打扰用户，要么不响应他。
 */
class ClipboardIntakeTest {

    private val pickupBody =
        "【菜鸟驿站】您的中通快递包裹已到向阳小区菜鸟驿站，取件码5-2-7712，货架3排12号，营业时间08:00-20:00"

    private val codeOnlyBody = "8-9-0765"

    private val verificationBody = "您的验证码是610920，请勿泄露给他人"

    private val marketingBody = "【某品牌】双十一优惠券已到账，领取入口限时折扣，退订回T"

    private fun auto(clipboard: String?, draft: String = "", known: Set<String> = emptySet()) =
        ClipboardIntake.autoPrefill(clipboard, draft, known)

    // ---------- 自动填：四道拒绝都要有名字 ----------

    @Test
    fun `像取件的短信被自动填，内容一个字都不改`() {
        val verdict = auto(pickupBody)
        assertTrue(verdict is ClipboardVerdict.Fill)
        assertEquals(pickupBody, (verdict as ClipboardVerdict.Fill).text)
    }

    @Test
    fun `填的是修剪首尾空白的原文，正文本身不许改写`() {
        // 归一化只做到指纹那一层：屏上要回显的是他能对得上手机里那条短信的样子。
        val verdict = auto("  \n$pickupBody  \n")
        assertEquals(pickupBody, (verdict as ClipboardVerdict.Fill).text)
    }

    @Test
    fun `验证码那类不自动填`() {
        assertEquals(ClipboardSkip.NOT_PICKUP, auto(verificationBody).skip())
    }

    @Test
    fun `营销短信那类不自动填，哪怕它写着领取`() {
        // 「优惠券…领取入口…限时折扣…退订」——负向锚赢，因为它没有强锚定担保。
        assertEquals(ClipboardSkip.NOT_PICKUP, auto(marketingBody).skip())
    }

    @Test
    fun `只有一个码串时不自动填`() {
        // 光一串码没有语境，等于不知道它是取件码、验证码还是订单号。
        // 这一格由显式粘贴兜住，不由判据放宽兜住——放宽的那一档会同时放过验证码。
        assertEquals(ClipboardSkip.NOT_PICKUP, auto(codeOnlyBody).skip())
    }

    @Test
    fun `读不到剪贴板就是读不到，不编一个原因`() {
        assertEquals(ClipboardSkip.EMPTY, auto(null).skip())
        assertEquals(ClipboardSkip.EMPTY, auto("   ").skip())
    }

    @Test
    fun `导入页正有内容时谁都不许动它`() {
        val verdict = auto(pickupBody, draft = "我正在手打的那一条")
        assertEquals(ClipboardSkip.HAS_DRAFT, verdict.skip())
    }

    @Test
    fun `草稿这一问排在最前，连重复正文都不先判`() {
        // 顺序本身就是判据：「不许清掉用户打好的字」优先于一切关于剪贴板内容的讨论。
        val verdict = auto(pickupBody, draft = "半截字", known = setOf(bodyFingerprint(pickupBody)))
        assertEquals(ClipboardSkip.HAS_DRAFT, verdict.skip())
    }

    @Test
    fun `库里已经见过的那条正文不再填一次`() {
        val verdict = auto(pickupBody, known = setOf(bodyFingerprint(pickupBody)))
        assertEquals(ClipboardSkip.KNOWN, verdict.skip())
    }

    @Test
    fun `重复的判据和消息账本同源，全角写法照样认得`() {
        // 指纹走 NFKC，所以全角码值与半角码值是**同一件事**；这一格判错的代价是让用户
        // 对着同一条短信再手动导一遍。
        val fullWidthCode = pickupBody.replace("5-2-7712", "５-２-７７１２")
        assertNotEquals("样本没换出全角写法，这条测试等于没跑", fullWidthCode, pickupBody)
        assertEquals(bodyFingerprint(pickupBody), bodyFingerprint(fullWidthCode))
        assertEquals(ClipboardSkip.KNOWN, auto(fullWidthCode, known = setOf(bodyFingerprint(pickupBody))).skip())
    }

    @Test
    fun `一整页聊天不自动填`() {
        val chat = "同事：帮你带了个件\n我：好\n" + pickupBody.repeat(12)
        assertTrue(chat.length > ClipboardIntake.MAX_AUTO_IMPORT_LENGTH)
        assertEquals(ClipboardSkip.TOO_LONG, auto(chat).skip())
    }

    // ---------- 显式粘贴：不对称是刻意的 ----------

    @Test
    fun `用户按了粘贴，草稿非空也照填`() {
        val verdict = ClipboardIntake.explicitPaste(codeOnlyBody)
        assertEquals(codeOnlyBody, (verdict as ClipboardVerdict.Fill).text)
    }

    @Test
    fun `用户按了粘贴，一整页聊天也照填`() {
        val chat = pickupBody.repeat(40)
        assertTrue((ClipboardIntake.explicitPaste(chat) as ClipboardVerdict.Fill).text == chat.trim())
    }

    @Test
    fun `用户按了粘贴，不像取件的也照填——他要的就是这段`() {
        assertTrue(ClipboardIntake.explicitPaste(verificationBody) is ClipboardVerdict.Fill)
    }

    @Test
    fun `显式粘贴唯一会拒的还是空`() {
        assertEquals(ClipboardSkip.EMPTY, ClipboardIntake.explicitPaste(null).skip())
        assertEquals(ClipboardSkip.EMPTY, ClipboardIntake.explicitPaste(" \n").skip())
    }

    private fun ClipboardVerdict.skip(): ClipboardSkip = (this as ClipboardVerdict.Skip).reason
}
