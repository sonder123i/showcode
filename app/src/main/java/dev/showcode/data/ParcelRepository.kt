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
import dev.showcode.core.ExistingParcel
import dev.showcode.core.HealthReport
import dev.showcode.core.MessageSkip
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.ParcelAction
import dev.showcode.core.PointAlias
import dev.showcode.core.resolvePointKey
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.UnrecognizedAction
import dev.showcode.core.UnrecognizedState
import dev.showcode.core.UrgencyThresholds
import dev.showcode.core.bodyFingerprint
import dev.showcode.core.healthReport
import dev.showcode.core.messageFingerprint
import dev.showcode.core.muteKey
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.MessageDomain
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.ParseRule
import dev.showcode.core.parse.ParseRules
import dev.showcode.core.parse.PickupCodeParser
import dev.showcode.core.parse.ParsedParcel
import dev.showcode.core.planIngest
import dev.showcode.core.pointKey
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

/** 一次刷新的结果。报「刚刚同步」而不报同步到了什么，等于让用户靠猜。 */
data class SyncResult(val added: Int, val updated: Int, val at: Long)

data class AppData(
    /** false = 还没读过库。首页的三种空态语义完全不同，不能把「加载中」当成「还没有快递」。 */
    val loaded: Boolean = false,
    val parcels: List<Parcel> = emptyList(),
    /** 只含 state=NEW 的。用户忽略过、或已经手动提取过的不在这里——那正是修复的重点。 */
    val unrecognized: List<UnrecognizedMessage> = emptyList(),
    /** 取件点合并登记，键是源点的 pointKey。分组时在读取侧解析，parcel 行不被改写。 */
    val aliases: Map<String, PointAlias> = emptyMap(),
    /** 用户已经否认过的「可能是同一个点」对，见 [dev.showcode.core.pairKey]。 */
    val dismissals: Set<String> = emptySet(),
    /** 被「不再显示此类」收掉的发件人。设置页要能列出来，否则用户屏蔽错了没有反悔的地方。 */
    val mutedSenders: List<String> = emptyList(),
    /** 用户自己写的解析规则。和屏蔽名单同一个理由：写进去的东西必须看得见、删得掉。 */
    val rules: List<ParseRule> = emptyList(),
    val lastSyncAt: Long? = null,
    val syncing: Boolean = false,
    /** null = 这次启动还没刷过。 */
    val lastSyncResult: SyncResult? = null,
    /** 读短信库失败。失败时**不**盖 lastSyncAt，所以首页的红条不会消失。 */
    val lastSyncFailed: Boolean = false,
    val monthlyPicked: Int = 0,
    val lastPickupAt: Long? = null,
)

/**
 * 全部数据都在本机（brief 约束：用户数据永不出本机）。
 * 写操作走单线程 IO dispatcher，所以不需要事务锁；读永远是内存里的一份快照。
 *
 * 这里**不做任何判断**。一条消息进来该不该动库、动哪一行，全部在
 * [dev.showcode.core.planIngest] 里，因为那层以前不可测，才让「已取复活」和
 * 「忽略复活」在仓库里活了很久。
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
        // 演示数据不落库，所以这里看到的必然是真件：没件可取时「很久没同步」是正常的。
        hasUnpicked = _data.value.parcels.any { !it.picked },
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
     * 回填收件箱。
     * 没有这一步，用户装完 App 会看到空首页，而手机上明明躺着三个待取件——
     * Story 2 AC1 要的是「什么都不做码就出现」。
     *
     * 从 [days] 天窗口起步，之后按 [KEY_LAST_INGESTED] 游标增量走，
     * 但**故意往回多留 [BACKFILL_OVERLAP_MS]**：短信到达顺序不保证，
     * 只按游标往前走会把迟到的一条永久漏掉。重叠部分由账本挡着，不产生任何写。
     */
    fun backfillSms(days: Int = 14): Job = scope.launch {
        if (!hasSmsPermission()) return@launch
        _data.update { it.copy(syncing = true, lastSyncFailed = false) }
        val cursor = db.metaGet(KEY_LAST_INGESTED)?.toLongOrNull() ?: 0L
        val windowStart = System.currentTimeMillis() - days * DAY_MS
        val since = maxOf(windowStart, cursor - BACKFILL_OVERLAP_MS)
        var newest = cursor
        var added = 0
        var updated = 0
        val queryOk = runCatching {
            db.inTransaction {
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
                        val at = c.getLong(date)
                        if (at > newest) newest = at
                        val tally = process(
                            IncomingMessage(
                                sender = c.getString(addr) ?: "",
                                body = c.getString(body) ?: "",
                                receivedAt = at,
                                channel = Source.SMS,
                            )
                        )
                        added += tally.first
                        updated += tally.second
                    }
                }
            }
            true
        }.getOrDefault(false)

        if (queryOk) {
            if (newest > cursor) db.metaPut(KEY_LAST_INGESTED, newest.toString())
            db.pruneUnrecognized(System.currentTimeMillis(), DECIDED_TTL_MS, NEW_UNRECOGNIZED_CAP)
            stampSync()
            _data.update { it.copy(syncing = false, lastSyncResult = SyncResult(added, updated, System.currentTimeMillis())) }
        } else {
            // 失败时绝不盖时间戳。旧版无条件 stampSync()，于是权限被厂商悄悄收回之后，
            // 首页那句「可能不是最新」的红条再也不出现——App 在谎报数据是新鲜的。
            _data.update { it.copy(syncing = false, lastSyncFailed = true) }
        }
        reload()
    }

    /** 首页「立即刷新」：error-stale-data 的出口。 */
    fun refreshNow(): Job = backfillSms()

    /**
     * 手动导入 / 未识别页的手动提取。
     * 与自动收录走同一张表、同一套保守归一化，只是来源标成 MANUAL；
     * 时效依据给驿站默认关门时刻——手动导入的文本里通常没有营业时间。
     *
     * @return 是否真的写进去了。以前这里直接 `insertParcel` 而不先查已有行，
     *         于是同一个码同一个点再输一次会撞部分唯一索引；`insert()` 撞约束只返回 -1 不抛，
     *         界面照样跳到首页像是存好了——**码其实没进库**。
     */
    suspend fun manualAdd(
        code: String,
        pointName: String,
        lockerNo: String?,
        raw: String,
        owner: Owner = Owner.SELF,
        resolvesUnrecognizedOf: String? = null,
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
            put("raw", raw)
        }
        // 已有同码同点的未取件：更新那一条（补上的柜号、归属都要生效），而不是再插一条。
        val existing = db.findParcelForUpdate(code, key)?.takeIf { !it.picked }
        val stored = if (existing != null) {
            db.updateParcel(existing.id, values)
            true
        } else {
            values.put("picked", 0)
            db.insertParcel(values) > 0
        }
        if (stored) {
            // 提取成功就是把这条未识别**解决**掉，不是把它删掉。删掉的话下一次刷新
            // 同一条短信又被读进来，用户会看到自己刚处理完的那条原样回来。
            resolvesUnrecognizedOf?.let { body ->
                db.resolveUnrecognizedByBody(body, System.currentTimeMillis())
            }
            stampSync()
            reload()
        }
        stored
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** 忽略 = 表态。以前它是 DELETE，所以它的作用刚好等于零。 */
    fun dismissUnrecognized(id: Long): Job = scope.launch {
        db.setUnrecognizedState(listOf(id), UnrecognizedState.DISMISSED, System.currentTimeMillis())
        reload()
    }

    /** 只写名单，不动任何一条现有消息：可见性在读取侧按名单过滤，所以「恢复」是无损的。 */
    fun muteSender(sender: String): Job = scope.launch {
        val now = System.currentTimeMillis()
        db.muteSender(muteKey(sender), now)
        reload()
    }

    fun unmuteSender(key: String): Job = scope.launch {
        db.unmuteSender(key)
        reload()
    }

    /**
     * 处理一条消息，返回 (新增行数, 更新行数)。
     * 判断全在 [planIngest]，这里只负责取事实和执行。
     *
     * @param forceReplay 绕过「这条消息读过了」那道闸。**只有** [reprocessUnrecognized] 传 true：
     *   账本存在的全部理由是不必每轮重跑，而用户刚补了一条规则时恰恰需要重跑那几条。
     *   默认 false，所以刷新重放那条防线一个字都没动。
     */
    private fun process(message: IncomingMessage, forceReplay: Boolean = false): Pair<Int, Int> {
        val fingerprint = messageFingerprint(message.channel, message.sender, message.body)
        val outcome = PickupCodeParser.parse(message, enabledRules())
        val plan = planIngest(
            message = message,
            outcome = outcome,
            fingerprint = fingerprint,
            alreadySeen = !forceReplay && db.isMessageSeen(fingerprint),
            senderMuted = db.isSenderMuted(muteKey(message.sender)),
            existingParcels = existingFor(outcome),
            unrecognizedState = if (outcome is ParseOutcome.Rejected) {
                db.unrecognizedState(bodyFingerprint(message.body))
            } else {
                null
            },
        )
        if (plan.skip != MessageSkip.NONE) return 0 to 0

        var added = 0
        var updated = 0
        for (action in plan.parcels) {
            when (action) {
                is ParcelAction.Insert -> {
                    if (db.insertParcel(parcelValues(action.parsed, message, action.pointKey, unpicked = true)) > 0) added++
                }
                is ParcelAction.Update -> {
                    db.updateParcel(action.id, parcelValues(action.parsed, message, action.pointKey, unpicked = false))
                    updated++
                }
                // 这条消息说的件已经被取走了。什么都不做，也不记账——
                // 不记账是因为它没动库，下一次刷新重跑到这里结论仍然一样。
                is ParcelAction.AlreadyPicked -> Unit
            }
        }
        when (val write = plan.unrecognized) {
            is UnrecognizedAction.Insert -> db.insertUnrecognized(
                ContentValues().apply {
                    put("sender", write.sender)
                    put("body", write.body)
                    put("body_fp", write.bodyKey)
                    put("reason", write.reason.name)
                    put("domain", write.domain.name)
                    put("state", UnrecognizedState.NEW.name)
                    put("received_at", write.receivedAt)
                }
            )
            UnrecognizedAction.Suppressed, null -> Unit
        }
        if (plan.worthLedgering) db.markMessageSeen(fingerprint, System.currentTimeMillis(), summarize(outcome))
        return added to updated
    }

    /** 把裁决要用的「库里已有哪些同码同点的行」取出来。取数归 Repository，判断归 core。 */
    private fun existingFor(outcome: ParseOutcome): Map<Pair<String, String>, ExistingParcel> {
        if (outcome !is ParseOutcome.Parsed) return emptyMap()
        return buildMap {
            outcome.parcels.forEach { parsed ->
                val key = pointKey(parsed.pointName)
                db.findParcelForUpdate(parsed.code, key)?.let { put(parsed.code to key, it) }
            }
        }
    }

    private fun parcelValues(parsed: ParsedParcel, message: IncomingMessage, pointKey: String, unpicked: Boolean) =
        ContentValues().apply {
            put("code", parsed.code)
            put("locker_no", parsed.lockerNo)
            put("carrier", parsed.carrier)
            put("point_key", pointKey)
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
            if (unpicked) put("picked", 0)
        }

    private fun summarize(outcome: ParseOutcome): String = when (outcome) {
        ParseOutcome.Ignored -> "IGNORED"
        is ParseOutcome.Parsed -> "PARCEL:${outcome.parcels.size}"
        is ParseOutcome.Rejected -> "REJECTED:${outcome.reason.name}"
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

    // ---------- 解析规则 ----------

    /**
     * 当前启用中的规则。每次解析现读，不缓存快照：写规则的路径都排在这条单线程 scope 上，
     * 缓存要么晚一步生效（用户当场看不出差别，下次才发现——那是最难复查的 bug），
     * 要么就得在每一处写入后面手动失效。这张表最多 [ParseRules.MAX_RULES] 行，读它比省它便宜。
     */
    private fun enabledRules(): List<ParseRule> = db.allRules().filter { it.enabled }

    /**
     * 录入前的校验。返回 null 表示可用，否则是 [RuleReject] 里那把键（UI 负责翻成人话）。
     * [addParseRule] 会再验一遍——写路径不信任调用方。
     */
    fun validateRule(pattern: String, pointName: String): String? =
        ParseRules.validate(pattern, pointName, db.allRules().size)

    /**
     * 用一段样本原文当场跑一遍完整解析，把结论原样交给 UI。
     *
     * 这条出口是必需的：规则**只在内置一个码都没认出来时**才补位，所以完全存在这样一种规则
     * ——它字面上命中了那段文本，却因为内置已经抽出了码而永远不会生效。
     * 规则最坏的失效是看起来在工作、其实永不命中，而这件事在保存之前就能验出来。
     */
    fun previewRule(pattern: String, pointName: String, sample: IncomingMessage): ParseOutcome =
        PickupCodeParser.parse(sample, listOf(ParseRule(pattern = pattern, pointName = pointName)))

    /**
     * 存一条规则。返回新行 id；`<= 0` 表示没写进去（和 [ShowCodeDb.insertRule] 同一条规矩）。
     *
     * 落库之后必须紧接着重判库里那几条未识别——用户写规则是为了治**已经漏掉的那一条**，
     * 如果它只对以后的短信生效，这一屏就白做了一次。两段排在同一条串行 scope 上，
     * 所以重判读到的必然是刚存进去的那条规则。
     */
    fun addParseRule(pattern: String, pointName: String, lockerNo: String?): Job = scope.launch {
        val rejection = ParseRules.validate(pattern, pointName, db.allRules().size)
        if (rejection != null) return@launch
        val id = db.insertRule(pattern.trim(), pointName.trim(), lockerNo?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
        if (id > 0) reprocessUnrecognized()
        reload()
    }

    /**
     * 用当前规则表重判库里还挂着的未识别消息。返回救回来的条数。
     *
     * 三条刻意的取舍：
     * 1. 走 [planIngest] 那条一模一样的路（`forceReplay = true`），而不是自己插行——
     *    「同码同点已有未取行就更新而不是再插一条」这类判据一条都不能为了省事而绕。
     * 2. 命中即标 **RESOLVED** 而不是删掉。删掉等于用户什么也没说过，下一次回填又会插回同一条。
     * 3. 上界钉死在 [REPROCESS_CAP]：这一步由用户点「保存」触发，不是后台常驻，
     *    库里攒了几百条时不该一次全跑。
     */
    private fun reprocessUnrecognized(): Int {
        val rules = enabledRules()
        if (rules.isEmpty()) return 0
        val targets = ParseRules.reprocessTargets(db.newUnrecognizedBodies(REPROCESS_CAP), rules)
        var saved = 0
        for (target in targets) {
            val tally = process(
                IncomingMessage(
                    sender = target.sender,
                    body = target.body,
                    receivedAt = target.receivedAt,
                    channel = Source.SMS,
                ),
                forceReplay = true,
            )
            if (tally.first + tally.second > 0) saved++
        }
        db.resolveUnrecognizedByIds(targets.map { it.id }, System.currentTimeMillis())
        return saved
    }

    /** 开关一条规则。关掉之后已有的那些件**不**回退——规则只负责认码，不负责撤销历史。 */
    fun setRuleEnabled(id: Long, enabled: Boolean) {
        scope.launch {
            db.setRuleEnabled(id, enabled)
            reload()
        }
    }

    fun deleteParseRule(id: Long) {
        scope.launch {
            db.deleteRule(id)
            reload()
        }
    }

    private fun stampSync() {
        db.metaPut(KEY_LAST_SYNC, System.currentTimeMillis().toString())
    }

    private fun reload() {
        val now = System.currentTimeMillis()
        val muted = db.allMutedSenders().toSet()
        val previous = _data.value
        _data.value = AppData(
            loaded = true,
            parcels = db.allParcels(),
            // 被屏蔽发件人的那些仍然在库里，只是不显示——所以「恢复」能原样拿回来。
            unrecognized = db.allUnrecognized().filterNot { muteKey(it.sender) in muted },
            aliases = db.allAliases().associateBy { it.aliasKey },
            dismissals = db.allDismissals(),
            mutedSenders = muted.toList(),
            rules = db.allRules(),
            lastSyncAt = db.metaGet(KEY_LAST_SYNC)?.toLongOrNull(),
            syncing = false,
            lastSyncResult = previous.lastSyncResult,
            lastSyncFailed = previous.lastSyncFailed,
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
        const val KEY_LAST_INGESTED = "last_ingested_at"

        /** 增量回填往回多读这么多，好接住迟到/乱序的短信。重叠部分由账本挡。 */
        const val BACKFILL_OVERLAP_MS = 2 * DAY_MS

        /** 表态满这么久的未识别行不再留。 */
        const val DECIDED_TTL_MS = 30 * DAY_MS

        /** 待看的未识别上限。超了按时间丢最旧的——列表的意义是「现在去看」，不是档案。 */
        const val NEW_UNRECOGNIZED_CAP = 50

        /**
         * 保存一条规则时最多重判多少条未识别。
         * 这一步由用户点「保存」触发、跑在写线程上，所以要有上界；它取未识别的全部保留量
         * （[NEW_UNRECOGNIZED_CAP]），等于「列表里看得见的，一条都不会被漏判」。
         */
        const val REPROCESS_CAP = 50
    }
}

/** 未识别角标该数几条。外卖那类不进角标：它永远等不到识别成功，挂着只会让人忽略整条列表。 */
fun unrecognizedBadgeCount(messages: List<UnrecognizedMessage>): Int =
    messages.count { it.domain == MessageDomain.PARCEL }
