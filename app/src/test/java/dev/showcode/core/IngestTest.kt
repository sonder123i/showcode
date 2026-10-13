package dev.showcode.core

import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParsedParcel
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.UnrecognizedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 入库裁决的回归测试。这一层以前没有测试，于是三个症状共用同一个根因活了很久：
 * 去重的身份挂在「解析结果还活着吗」上，而不是挂在「这条消息我读过吗」上。
 * 已取 / 忽略 / 手动提取都是**离开活跃集**的动作，于是三者都会被下一次刷新复活。
 *
 * 每个用例名写的都是用户的动作，不是函数的分支——出问题的时候要先能复现他那一步。
 */
class IngestTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun msg(
        body: String = "【菜鸟驿站】取件码 5-1-2277 请到XX小区驿站取件",
        at: Long = t0,
        sender: String = "10690123",
        channel: Source = Source.SMS,
    ) = IncomingMessage(sender = sender, body = body, receivedAt = at, channel = channel)

    private fun parcel(code: String = "5-1-2277", pointName: String = "XX小区驿站") = ParsedParcel(
        code = code,
        lockerNo = null,
        carrier = "中通快递",
        pointName = pointName,
        pointKind = PointKind.STATION,
        closesAtMinuteOfDay = 21 * 60,
        freeUntilAt = null,
    )

    private fun existing(id: Long, picked: Boolean, receivedAt: Long, pickedAt: Long? = null) =
        ExistingParcel(id = id, picked = picked, pickedAt = pickedAt, receivedAt = receivedAt)

    private fun table(vararg rows: Pair<Pair<String, String>, ExistingParcel>) = rows.toMap()

    private val rejected = ParseOutcome.Rejected(UnrecognizedReason.NO_CODE_FOUND)

    // ---------- 第一道防线：同一条消息不得重放 ----------

    @Test
    fun `刷新重放同一条消息时整条跳过，一个字都不动`() {
        val plan = planIngest(
            message = msg(),
            outcome = ParseOutcome.Parsed(listOf(parcel())),
            fingerprint = "fp",
            alreadySeen = true,
        )
        assertEquals(MessageSkip.ALREADY_SEEN, plan.skip)
        assertTrue(plan.parcels.isEmpty())
        assertNull(plan.unrecognized)
        assertFalse(plan.worthLedgering)
    }

    @Test
    fun `指纹认的是消息内容而不是它什么时候到的`() {
        val a = messageFingerprint(Source.SMS, "10690123", "取件码 5-1-2277")
        val b = messageFingerprint(Source.SMS, "10690123", "取件码 5-1-2277 到了")
        assertNotEquals(a, b)
        // 空白与全角差异不该产生第二个身份：同一条短信被两个通道抄出来略有不同
        assertEquals(
            messageFingerprint(Source.SMS, "10690123", "取件码  5-1-2277\n"),
            messageFingerprint(Source.SMS, "10690123", "取件码　5-1-2277"),
        )
        // 通道要参与消息指纹：同一段文字由短信进来和由通知进来是两条不同的报道。
        assertNotEquals(a, messageFingerprint(Source.NOTIFICATION, "10690123", "取件码 5-1-2277"))
    }

    // ---------- 第二道防线：不同消息报道同一件已取走的件 ----------

    /** 这就是他说的那个症状：点了已取，再刷新，卡又回来了。 */
    @Test
    fun `已取的件不因另一条旧消息而复活`() {
        val key = pointKey("XX小区驿站")
        val plan = planIngest(
            message = msg(at = t0),
            outcome = ParseOutcome.Parsed(listOf(parcel())),
            fingerprint = "fp",
            existingParcels = table(("5-1-2277" to key) to existing(7, picked = true, receivedAt = t0, pickedAt = t0 + hour)),
        )
        assertEquals(
            ParcelAction.AlreadyPicked(7, parcel(), key),
            plan.parcels.single(),
        )
    }

    /** 反过来也必须成立：吞掉一个真码比多一张卡严重得多，所以拿不准就插新行。 */
    @Test
    fun `取件码复用必须生成新卡而不是被已取的那条挡掉`() {
        val key = pointKey("XX小区驿站")
        val plan = planIngest(
            message = msg(at = t0 + 30 * 24 * hour),
            outcome = ParseOutcome.Parsed(listOf(parcel())),
            fingerprint = "fp2",
            existingParcels = table(("5-1-2277" to key) to existing(7, picked = true, receivedAt = t0, pickedAt = t0 + hour)),
        )
        assertEquals(ParcelAction.Insert(parcel(), key), plan.parcels.single())
    }

    @Test
    fun `同一件的另一路报道更新原行而不是插一条`() {
        val key = pointKey("XX小区驿站")
        val plan = planIngest(
            message = msg(at = t0 + 1000, channel = Source.NOTIFICATION, sender = "菜鸟"),
            outcome = ParseOutcome.Parsed(listOf(parcel())),
            fingerprint = "fp",
            existingParcels = table(("5-1-2277" to key) to existing(7, picked = false, receivedAt = t0)),
        )
        assertEquals(ParcelAction.Update(7, parcel(), key), plan.parcels.single())
    }

    @Test
    fun `一条消息两个码各自决定不互相牵连`() {
        val key = pointKey("XX小区驿站")
        val plan = planIngest(
            message = msg(at = t0),
            outcome = ParseOutcome.Parsed(listOf(parcel("5-1-2277"), parcel("5-2-3391"))),
            fingerprint = "fp",
            existingParcels = table(("5-1-2277" to key) to existing(7, picked = false, receivedAt = t0)),
        )
        assertEquals(
            listOf(ParcelAction.Update(7, parcel("5-1-2277"), key), ParcelAction.Insert(parcel("5-2-3391"), key)),
            plan.parcels,
        )
    }

    // ---------- 未识别：用户的表态必须有持久效力 ----------

    /** 旧版把「忽略」实现成 DELETE，于是下一次刷新必然把它带回来——忽略等于零作用。 */
    @Test
    fun `忽略过的消息不再回到列表`() {
        for (state in listOf(UnrecognizedState.DISMISSED, UnrecognizedState.RESOLVED)) {
            val plan = planIngest(
                message = msg(),
                outcome = rejected,
                fingerprint = "fp",
                unrecognizedState = state,
            )
            assertEquals(state.toString(), UnrecognizedAction.Suppressed, plan.unrecognized)
            assertFalse(plan.worthLedgering)
        }
    }

    @Test
    fun `库里没有这条正文时才插未识别`() {
        val plan = planIngest(message = msg(), outcome = rejected, fingerprint = "fp")
        assertTrue(plan.unrecognized is UnrecognizedAction.Insert)
        assertEquals(UnrecognizedReason.NO_CODE_FOUND, (plan.unrecognized as UnrecognizedAction.Insert).reason)
    }

    @Test
    fun `屏蔽过的发件人连未识别都不落`() {
        val plan = planIngest(
            message = msg(),
            outcome = rejected,
            fingerprint = "fp",
            senderMuted = true,
        )
        assertEquals(MessageSkip.MUTED_SENDER, plan.skip)
        assertNull(plan.unrecognized)
    }

    @Test
    fun `未识别的身份是正文而不是发件人加正文`() {
        // 同一段话由号码和 App 各报一次是常态；旧版按 (sender, body) 精确匹配，于是列表里两条。
        assertEquals(bodyFingerprint("取件码 5-1-2277"), bodyFingerprint("取件码 5-1-2277"))
        assertNotEquals(bodyFingerprint("取件码 5-1-2277"), bodyFingerprint("取件码 5-1-2278"))
    }

    // ---------- 账本只记动过库的 ----------

    @Test
    fun `没动过库的消息不记账，好让解析器改进之后还有重判的机会`() {
        val ignored = planIngest(message = msg(), outcome = ParseOutcome.Ignored, fingerprint = "fp")
        assertFalse(ignored.worthLedgering)

        val suppressed = planIngest(
            message = msg(),
            outcome = rejected,
            fingerprint = "fp",
            unrecognizedState = UnrecognizedState.NEW,
        )
        assertFalse(suppressed.worthLedgering)

        // 已取件被挡住时也没动库，但这条**不该**被记账之外……
        val key = pointKey("XX小区驿站")
        val picked = planIngest(
            message = msg(),
            outcome = ParseOutcome.Parsed(listOf(parcel())),
            fingerprint = "fp",
            existingParcels = table(("5-1-2277" to key) to existing(7, picked = true, receivedAt = t0, pickedAt = t0 + hour)),
        )
        assertFalse(picked.worthLedgering)

        val inserted = planIngest(message = msg(), outcome = ParseOutcome.Parsed(listOf(parcel())), fingerprint = "fp")
        assertTrue(inserted.worthLedgering)
    }

    // ---------- 屏蔽键 ----------

    @Test
    fun `屏蔽键把号码里的分隔符折掉，让同一个发件人只有一个键`() {
        assertEquals("10690123", muteKey(" 106-90123 "))
        assertEquals("菜鸟", muteKey("菜鸟"))
        assertEquals("", muteKey("   "))
    }

    @Test
    fun `点位名不确定的消息带着已认出的码进未识别`() {
        val outcome = ParseOutcome.Rejected(UnrecognizedReason.POINT_NAME_UNCONFIRMED, listOf("7902"))
        val plan = planIngest(message = msg(), outcome = outcome, fingerprint = "fp")
        assertEquals(UnrecognizedReason.POINT_NAME_UNCONFIRMED, (plan.unrecognized as UnrecognizedAction.Insert).reason)
    }
}
