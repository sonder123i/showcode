package dev.showcode.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.showcode.R
import dev.showcode.core.DAY_MS
import dev.showcode.core.DEFAULT_CLOSING_MINUTE
import dev.showcode.core.HealthReport
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointAlias
import dev.showcode.core.resolvePointKey
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.UrgencyThresholds
import dev.showcode.core.healthReport
import dev.showcode.core.pointKey
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.PickupCodeParser
import dev.showcode.core.parse.ParsedParcel
import dev.showcode.core.parse.UnrecognizedReason
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppData(
    /** false = 还没读过库。首页的三种空态语义完全不同，不能把「加载中」当成「还没有快递」。 */
    val loaded: Boolean = false,
    val parcels: List<Parcel> = emptyList(),
    val unrecognized: List<UnrecognizedMessage> = emptyList(),
    /** 取件点合并登记，键是源点的 pointKey。分组时在读取侧解析，parcel 行不被改写。 */
    val aliases: Map<String, PointAlias> = emptyMap(),
    /** 用户已经否认过的「可能是同一个点」对，见 [dev.showcode.core.pairKey]。 */
    val dismissals: Set<String> = emptySet(),
    val lastSyncAt: Long? = null,
    val monthlyPicked: Int = 0,
    val lastPickupAt: Long? = null,
)

/**
 * 全部数据都在本机（brief 约束：用户数据永不出本机）。
 * 写操作走单线程 IO dispatcher，所以不需要事务锁；读永远是内存里的一份快照。
 */
class ParcelRepository(private val context: Context, private val db: ShowCodeDb) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    val prefs = Prefs(context)

    /**
     * 临期阈值。frame 锁定「给默认值 + 允许用户调」，设置页那两个滑块改的就是它。
     * 用 StateFlow 是因为首页分组与桌面组件的倒计时都消费它，改完必须立刻重算。
     */
    private val _thresholds = MutableStateFlow(prefs.thresholds)
    val thresholds: StateFlow<UrgencyThresholds> = _thresholds.asStateFlow()

    fun setLockerDueSoonHours(hours: Int) {
        prefs.lockerDueSoonHours = hours
        publishThresholds()
    }

    fun setStationDueSoonMinutes(minutes: Int) {
        prefs.stationDueSoonMinutes = minutes
        publishThresholds()
    }

    fun restoreThresholdDefaults() {
        prefs.restoreThresholdDefaults()
        publishThresholds()
    }

    private fun publishThresholds() {
        _thresholds.value = prefs.thresholds
        scope.launch { reload() }
    }

    /**
     * 权限健康自检。story-5：权限失效是静默的，必须常驻自检而不是 onboarding 查一次。
     * 小米的「通知类短信」无法程序化查询，所以按厂商降级成体检表里的人工确认项。
     */
    fun healthReport(now: Long): HealthReport = healthReport(
        smsGranted = hasSmsPermission(),
        notificationEnabled = hasNotificationAccess(),
        lastSyncAt = _data.value.lastSyncAt,
        now = now,
        isXiaomi = Build.MANUFACTURER.equals("xiaomi", true) || Build.MANUFACTURER.equals("redmi", true),
    )

    /** 数据或阈值变化后通知外部常驻表面（桌面组件）。批 2 由 widget 注册。 */
    var onDataChange: (() -> Unit)? = null

    fun start() {
        scope.launch { reload() }
    }

    /** 短信广播 / 通知监听进来。返回 Job，好让 BroadcastReceiver 用 goAsync() 等它落库。 */
    fun ingest(message: IncomingMessage): Job = scope.launch {
        process(message)
        // 实时收到消息本身就证明收录是活的，所以这里也盖时间戳：
        // 否则首页会在明明刚进了一条短信时仍然报「可能不是最新」。
        stampSync()
        reload()
    }

    fun markPicked(id: Long, at: Long = System.currentTimeMillis()) {
        // 先同步改内存态再落库：现场模式下任何等待都是失败（edge loading-submit 红线），
        // 「已取」的视觉反馈必须和点击同一帧发生。
        _data.update { snapshot ->
            snapshot.copy(parcels = snapshot.parcels.map { if (it.id == id) it.copy(picked = true, pickedAt = at) else it })
        }
        scope.launch {
            db.updateParcel(id, ContentValues().apply { put("picked", 1); put("picked_at", at) })
            reload()
        }
    }

    /** 改归属（story-7 最小版：手动打标签）。管理面，不需要现场那种同帧反馈。 */
    fun setParcelOwner(id: Long, owner: Owner) {
        scope.launch {
            db.updateParcel(id, ContentValues().apply { put("owner", owner.name) })
            reload()
        }
    }

    fun undoPicked(id: Long) {
        _data.update { snapshot ->
            snapshot.copy(parcels = snapshot.parcels.map { if (it.id == id) it.copy(picked = false, pickedAt = null) else it })
        }
        scope.launch {
            db.updateParcel(id, ContentValues().apply { put("picked", 0); putNull("picked_at") })
            reload()
        }
    }

    /**
     * 回填最近 [days] 天的收件箱。
     * 没有这一步，用户装完 App 会看到空首页，而手机上明明躺着三个待取件——
     * Story 2 AC1 要的是「什么都不做码就出现」。
     */
    fun backfillSms(days: Int = 14): Job = scope.launch {
        if (!hasSmsPermission()) return@launch
        val since = System.currentTimeMillis() - days * DAY_MS
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    process(
                        IncomingMessage(
                            sender = c.getString(addr) ?: "",
                            body = c.getString(body) ?: "",
                            receivedAt = c.getLong(date),
                            channel = Source.SMS,
                        )
                    )
                }
            }
        }
        stampSync()
        reload()
    }

    /** 首页「立即刷新」：error-stale-data 的出口。 */
    fun refreshNow(): Job = backfillSms()

    /**
     * 手动导入 / 未识别页的手动提取。
     * 与自动收录走同一张表、同一套保守归一化，只是来源标成 MANUAL；
     * 时效依据给驿站默认关门时刻——手动导入的文本里通常没有营业时间。
     *
     * @return 是否真的写进去了。以前这里直接 `insertParcel` 而不先查 `findUnpicked`，
     *         于是同一个码同一个点再输一次会撞部分唯一索引；`insert()` 撞约束只返回 -1 不抛，
     *         界面照样跳到首页像是存好了——**码其实没进库**。
     */
    suspend fun manualAdd(
        code: String,
        pointName: String,
        lockerNo: String?,
        raw: String,
        owner: Owner = Owner.SELF,
    ): Boolean = withContext(Dispatchers.IO) {
        val key = pointKey(pointName)
        val values = ContentValues().apply {
            put("code", code)
            put("locker_no", lockerNo)
            put("carrier", context.getString(R.string.manual_import_carrier))
            put("point_key", key)
            put("point_name", pointName)
            put("point_kind", PointKind.STATION.name)
            put("owner", owner.name)
            put("source", Source.MANUAL.name)
            put("received_at", System.currentTimeMillis())
            putNull("free_until_at")
            put("closes_at_minute", DEFAULT_CLOSING_MINUTE[PointKind.STATION])
            put("picked", 0)
            put("raw", raw)
        }
        // 已有同码同点的未取件：更新那一条（补上的柜号、归属都要生效），而不是再插一条。
        val existing = db.findUnpicked(code, key)
        val stored = if (existing != null) {
            db.updateParcel(existing, values)
            true
        } else {
            db.insertParcel(values) > 0
        }
        if (stored) {
            stampSync()
            reload()
        }
        stored
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun clearUnrecognized(id: Long): Job = scope.launch {
        db.deleteUnrecognized(id)
        reload()
    }

    private fun process(message: IncomingMessage) {
        when (val outcome = PickupCodeParser.parse(message)) {
            ParseOutcome.Ignored -> Unit
            is ParseOutcome.Parsed -> outcome.parcels.forEach { upsert(it, message) }
            is ParseOutcome.Rejected -> recordUnrecognized(message, outcome.reason)
        }
    }

    private fun upsert(parsed: ParsedParcel, message: IncomingMessage) {
        val key = PickupCodeParser.pointKeyOf(parsed)
        val values = ContentValues().apply {
            put("code", parsed.code)
            put("locker_no", parsed.lockerNo)
            put("carrier", parsed.carrier)
            put("point_key", key)
            put("point_name", parsed.pointName)
            put("point_kind", parsed.pointKind.name)
            put("owner", message.owner.name)
            put("source", message.channel.name)
            put("received_at", message.receivedAt)
            if (parsed.freeUntilAt != null) put("free_until_at", parsed.freeUntilAt) else putNull("free_until_at")
            if (parsed.closesAtMinuteOfDay != null) {
                put("closes_at_minute", parsed.closesAtMinuteOfDay)
            } else {
                putNull("closes_at_minute")
            }
            put("raw", message.body)
        }
        // 同一件被短信和通知各报一次是常态：按 (code, point_key) 去重，保留原 id 与已取状态。
        val existing = db.findUnpicked(parsed.code, key)
        if (existing == null) {
            values.put("picked", 0)
            db.insertParcel(values)
        } else {
            db.updateParcel(existing, values)
        }
    }

    private fun recordUnrecognized(message: IncomingMessage, reason: UnrecognizedReason) {
        if (db.hasUnrecognized(message.sender, message.body)) return
        db.insertUnrecognized(
            ContentValues().apply {
                put("sender", message.sender)
                put("body", message.body)
                put("reason", reason.name)
                put("received_at", message.receivedAt)
            }
        )
    }

    /**
     * 合并两个取件点（story-6）。只登记一条别名，不动任何 parcel 行——
     * 所以 [undoMerge] 删掉登记就等于完整还原，AC3 不需要"回滚"这种危险动作。
     */
    fun mergePoints(record: PointAlias) {
        scope.launch {
            db.putAlias(record)
            reload()
        }
    }

    /** 拆开一次合并。返回被撤销的登记，好让撤销条能原样放回去。 */
    fun undoMerge(aliasKey: String) {
        scope.launch {
            db.deleteAlias(aliasKey)
            reload()
        }
    }

    /** 某个点身上背着哪些合并——取件点详情的「合并历史」。 */
    fun mergeHistoryFor(canonicalKey: String): List<PointAlias> =
        _data.value.aliases.values.filter { resolvePointKey(it.targetKey, _data.value.aliases) == canonicalKey }

    /** 「不是同一个点」。本地表态优先，V2 的社区别名数据不得覆盖（edge boundary-alias-conflict）。 */
    fun dismissSuspect(pairKey: String) {
        scope.launch {
            db.putDismissal(pairKey, System.currentTimeMillis())
            reload()
        }
    }

    private fun stampSync() {
        db.metaPut(KEY_LAST_SYNC, System.currentTimeMillis().toString())
    }

    private fun reload() {
        val now = System.currentTimeMillis()
        _data.value = AppData(
            loaded = true,
            parcels = db.allParcels(),
            unrecognized = db.allUnrecognized(),
            aliases = db.allAliases().associateBy { it.aliasKey },
            dismissals = db.allDismissals(),
            lastSyncAt = db.metaGet(KEY_LAST_SYNC)?.toLongOrNull(),
            monthlyPicked = db.countPickedSince(startOfCurrentMonth(now)),
            lastPickupAt = db.lastPickupAt(),
        )
        runCatching { onDataChange?.invoke() }
    }

    private fun startOfCurrentMonth(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
