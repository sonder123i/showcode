package dev.showcode.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.showcode.core.ExistingParcel
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointAlias
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.UnrecognizedState
import dev.showcode.core.bodyFingerprint
import dev.showcode.core.enumOrDefault
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.MessageDomain
import dev.showcode.core.parse.MessageDomains
import dev.showcode.core.parse.ParseRule
import dev.showcode.core.parse.UnrecognizedRecord
import dev.showcode.core.parse.UnrecognizedReason

/**
 * 一条没认出来的取件消息。原文必须留着——用户要靠原文判断自己漏了什么。
 *
 * [state] 是用户对这条消息表过的态。它和 [dev.showcode.core.UnrecognizedState.RESOLVED]
 * 一起构成「决定日志」而不是「待办列表」：以前「忽略」是 DELETE，
 * 于是下一次刷新把同一条短信重新读进来时，库里根本没有记录可查，只能再插一条。
 */
data class UnrecognizedMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val reason: UnrecognizedReason,
    val receivedAt: Long,
    val domain: MessageDomain,
)

/**
 * 用 SQLiteOpenHelper 而不是 Room：批 1 只有两张表 + 一张键值表，
 * Room 要拖进 KSP 与一整套注解处理器，在这台机器上是纯粹的构建风险，换不到任何东西。
 * 表结构稳定后若要迁移，成本也只在这一层。
 */
class ShowCodeDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE parcel (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              code TEXT NOT NULL,
              locker_no TEXT,
              carrier TEXT NOT NULL,
              point_key TEXT NOT NULL,
              point_name TEXT NOT NULL,
              point_kind TEXT NOT NULL,
              owner TEXT NOT NULL,
              source TEXT NOT NULL,
              received_at INTEGER NOT NULL,
              free_until_at INTEGER,
              closes_at_minute INTEGER,
              picked INTEGER NOT NULL DEFAULT 0,
              picked_at INTEGER,
              raw TEXT NOT NULL
            )
            """.trimIndent()
        )
        // 去重只在「未取」的件之间做：取件码会被复用，取过的旧件不该挡住新到的同码件。
        // 用部分索引而不是 (code, point_key) 全表唯一索引，正是为了这个。
        //
        // 注意这条索引**不是**防重放的那道闸——防重放在 message_seen 表。
        // 曾经它就是：`ParcelRepository` 只查 `picked = 0` 的行，于是已取件对去重不可见，
        // 每次刷新都给同一个码插一条新卡。现在它只兜「同码同点同时未取」这一种。
        db.execSQL("CREATE UNIQUE INDEX parcel_dedupe ON parcel(code, point_key) WHERE picked = 0")
        db.execSQL("CREATE INDEX parcel_point ON parcel(point_key, picked)")
        db.execSQL("CREATE INDEX parcel_lookup ON parcel(code, point_key, received_at DESC)")
        db.execSQL(
            """
            CREATE TABLE unrecognized (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              sender TEXT NOT NULL,
              body TEXT NOT NULL,
              body_fp TEXT NOT NULL DEFAULT '',
              reason TEXT NOT NULL,
              domain TEXT NOT NULL DEFAULT 'PARCEL',
              state TEXT NOT NULL DEFAULT 'NEW',
              decided_at INTEGER,
              received_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX unrecognized_state ON unrecognized(state, received_at DESC)")
        db.execSQL("CREATE INDEX unrecognized_fp ON unrecognized(body_fp)")
        db.execSQL("CREATE TABLE meta (mkey TEXT PRIMARY KEY, mvalue TEXT NOT NULL)")
        // 源消息账本。整条链路唯一能回答「这条消息我到底读过没有」的东西——
        // 解析结果回答不了，因为「已取」「忽略」「已提取」都会让结果离开可比较的集合。
        db.execSQL(
            """
            CREATE TABLE message_seen (
              fp TEXT PRIMARY KEY,
              seen_at INTEGER NOT NULL,
              outcome TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE sender_mute (mute_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        // 合并登记。parcel 表里没有外键指向它——别名只在读取侧解析，
        // 所以「拆开」就是删掉这一行，不需要把任何包裹搬回去。
        db.execSQL(
            """
            CREATE TABLE point_alias (
              alias_key TEXT PRIMARY KEY,
              alias_name TEXT NOT NULL,
              target_key TEXT NOT NULL,
              target_name TEXT NOT NULL,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 用户已经否认过的疑似重复对。V2 的社区别名数据不得覆盖本地表态，所以要先查这张表。
        db.execSQL("CREATE TABLE point_dismiss (pair_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        // 用户自己写的解析规则。`pattern` 是一段由用户提供、在解析时被编译的正则，
        // 因此它的执行位置只可能在 [dev.showcode.core.parse.ParseRules] 里那条只读路径上：
        // 规则只做加法（内置认出件时一律不参与），且坏正则在录入时就被拒、解析时静默失效。
        // 这里不存「命中过什么」——一条规则该不该生效由当下这次解析决定，历史记录不算判据。
        db.execSQL(
            """
            CREATE TABLE parse_rule (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              pattern TEXT NOT NULL,
              point_name TEXT NOT NULL,
              locker_no TEXT,
              enabled INTEGER NOT NULL DEFAULT 1,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 这里绝不能走「删表重建」——那会把用户已经收进来的取件码全清掉。
        if (oldVersion < 2) {
            // v1 → v2：story-6 的两张表是纯新增，旧库里没有，直接补建即可。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS point_alias (
                  alias_key TEXT PRIMARY KEY,
                  alias_name TEXT NOT NULL,
                  target_key TEXT NOT NULL,
                  target_name TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS point_dismiss (pair_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        }
        if (oldVersion < 3) upgradeToV3(db)
        if (oldVersion < 4) {
            // v3 → v4：解析规则表是纯新增，旧库里没有，补建即可。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS parse_rule (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  pattern TEXT NOT NULL,
                  point_name TEXT NOT NULL,
                  locker_no TEXT,
                  enabled INTEGER NOT NULL DEFAULT 1,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v2 → v3：账本、发件人屏蔽、未识别的表态列，外加一次脏数据收口。
     *
     * 收口必须在加列之后、`reload()` 之前跑完：老库里躺着「已取 → 刷新 → 复活」攒下的重复行，
     * 它们共享同一个 (code, point_key, raw)，留下哪一条都不对，除了最早那条——
     * 它是真正被用户点过「已取」的那一行，复活出来的都排在它后面。
     */
    private fun upgradeToV3(db: SQLiteDatabase) {
        addColumnIfMissing(db, "unrecognized", "body_fp", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(db, "unrecognized", "domain", "TEXT NOT NULL DEFAULT 'PARCEL'")
        addColumnIfMissing(db, "unrecognized", "state", "TEXT NOT NULL DEFAULT 'NEW'")
        addColumnIfMissing(db, "unrecognized", "decided_at", "INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS unrecognized_state ON unrecognized(state, received_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS unrecognized_fp ON unrecognized(body_fp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS parcel_lookup ON parcel(code, point_key, received_at DESC)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_seen (
              fp TEXT PRIMARY KEY,
              seen_at INTEGER NOT NULL,
              outcome TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS sender_mute (mute_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")

        // 旧库里的未识别行既没有正文指纹也没有域。SQLite 没有 sha256，所以在 Kotlin 侧补。
        // 域必须一起补：老库里躺着的美团/蜂鸟短信如果仍然算 PARCEL，
        // 用户升级之后打开这一屏看到的还是同一排牛皮癣——那这次分流对他等于没发生。
        db.rawQuery("SELECT id, body FROM unrecognized", null).use { c ->
            val ids = ArrayList<Long>()
            val bodies = ArrayList<String>()
            while (c.moveToNext()) {
                ids.add(c.getLong(0))
                bodies.add(c.getString(1))
            }
            ids.forEachIndexed { i, id ->
                db.update(
                    "unrecognized",
                    ContentValues().apply {
                        put("body_fp", bodyFingerprint(bodies[i]))
                        put("domain", MessageDomains.classify(bodies[i]).name)
                    },
                    "id = ?",
                    arrayOf(id.toString()),
                )
            }
        }

        // 同一条正文被不同发件人各记一次（号码 + App 通知）是旧版 (sender, body) 精确匹配的产物。
        db.execSQL("DELETE FROM unrecognized WHERE id NOT IN (SELECT MIN(id) FROM unrecognized GROUP BY body_fp)")
        // 「已取 → 刷新」攒下的复活行。
        db.execSQL("DELETE FROM parcel WHERE id NOT IN (SELECT MIN(id) FROM parcel GROUP BY code, point_key, raw)")
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, type: String) {
        val present = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(name) else null }.any { it == column }
        }
        if (!present) db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
    }

    // ---------- 事务 ----------

    /** 回填一次要跑几百次写，逐条 autocommit 就是几百次 fsync。包起来。 */
    fun <T> inTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    // ---------- 源消息账本 ----------

    fun isMessageSeen(fp: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM message_seen WHERE fp = ? LIMIT 1",
        arrayOf(fp),
    ).use { it.moveToFirst() }

    fun markMessageSeen(fp: String, at: Long, outcome: String) {
        writableDatabase.insertWithOnConflict(
            "message_seen",
            null,
            ContentValues().apply { put("fp", fp); put("seen_at", at); put("outcome", outcome) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    // ---------- 发件人屏蔽 ----------

    fun isSenderMuted(muteKey: String): Boolean = muteKey.isNotEmpty() && readableDatabase.rawQuery(
        "SELECT 1 FROM sender_mute WHERE mute_key = ? LIMIT 1",
        arrayOf(muteKey),
    ).use { it.moveToFirst() }

    fun muteSender(muteKey: String, at: Long) {
        if (muteKey.isEmpty()) return
        writableDatabase.insertWithOnConflict(
            "sender_mute",
            null,
            ContentValues().apply { put("mute_key", muteKey); put("created_at", at) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun allMutedSenders(): List<String> = readableDatabase
        .rawQuery("SELECT mute_key FROM sender_mute ORDER BY created_at DESC", null)
        .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun unmuteSender(muteKey: String) {
        writableDatabase.delete("sender_mute", "mute_key = ?", arrayOf(muteKey))
    }

    // ---------- 包裹 ----------

    fun allParcels(): List<Parcel> = readableDatabase.queryAll().use { c -> buildList { while (c.moveToNext()) add(c.toParcel()) } }

    /**
     * 这条消息说的那件，库里现在是什么状态。
     *
     * **不带 `picked = 0` 过滤**——旧版带了，于是已取行对去重不可见，
     * 刷新重放的旧短信每次都绕过它插一条新卡。优先取未取的那行：
     * 同一码被复用后新旧并存时，要更新的是还活着的那件。
     */
    fun findParcelForUpdate(code: String, pointKey: String): ExistingParcel? = readableDatabase.rawQuery(
        "SELECT id, picked, picked_at, received_at FROM parcel" +
            " WHERE code = ? AND point_key = ? ORDER BY picked ASC, received_at DESC LIMIT 1",
        arrayOf(code, pointKey),
    ).use { c ->
        if (!c.moveToFirst()) return@use null
        ExistingParcel(
            id = c.getLong(0),
            picked = c.getInt(1) != 0,
            pickedAt = if (c.isNull(2)) null else c.getLong(2),
            receivedAt = c.getLong(3),
        )
    }

    /**
     * 插入新行。`SQLiteDatabase.insert()` 撞约束时**返回 -1 而不是抛**，
     * 所以返回值 ≤ 0 就是「没写进去」。调用方必须检查——不检查的话界面会跳到首页
     * 像是存好了，而那个码根本不在库里。
     */
    fun insertParcel(values: ContentValues): Long = writableDatabase.insert("parcel", null, values)

    fun updateParcel(id: Long, values: ContentValues) {
        writableDatabase.update("parcel", values, "id = ?", arrayOf(id.toString()))
    }

    // ---------- 未识别 ----------

    fun allUnrecognized(): List<UnrecognizedMessage> = readableDatabase
        .rawQuery(
            "SELECT id, sender, body, reason, received_at, domain FROM unrecognized" +
                " WHERE state = 'NEW' ORDER BY received_at DESC",
            null,
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        UnrecognizedMessage(
                            id = c.getLong(0),
                            sender = c.getString(1),
                            reason = enumOrDefault(c.getString(3), UnrecognizedReason.NO_CODE_FOUND),
                            body = c.getString(2),
                            receivedAt = c.getLong(4),
                            domain = enumOrDefault(c.getString(5), MessageDomain.PARCEL),
                        )
                    )
                }
            }
        }

    /** 这条正文在库里的现状。返回 null 才是「没见过」。 */
    fun unrecognizedState(bodyFp: String): UnrecognizedState? = readableDatabase.rawQuery(
        "SELECT state FROM unrecognized WHERE body_fp = ? LIMIT 1",
        arrayOf(bodyFp),
    ).use { if (!it.moveToFirst()) null else enumOrDefault(it.getString(0), UnrecognizedState.NEW) }

    fun insertUnrecognized(values: ContentValues) {
        writableDatabase.insert("unrecognized", null, values)
    }

    /** 表态是 UPDATE 不是 DELETE。删掉就等于用户什么也没说过。 */
    fun setUnrecognizedState(ids: List<Long>, state: UnrecognizedState, at: Long) {
        if (ids.isEmpty()) return
        val args = ArrayList<Any?>(ids.size + 2).apply {
            add(state.name)
            add(at)
            addAll(ids)
        }.toTypedArray()
        writableDatabase.execSQL(
            "UPDATE unrecognized SET state = ?, decided_at = ? WHERE id IN (${ids.joinToString(",") { "?" }})",
            args,
        )
    }

    /** 手动提取成功 = 这条已经解决了。命中 0 行是无害的：手动导入未必是从未识别页进来的。 */
    fun resolveUnrecognizedByBody(body: String, at: Long) {
        writableDatabase.execSQL(
            "UPDATE unrecognized SET state = 'RESOLVED', decided_at = ? WHERE body_fp = ?",
            arrayOf<Any>(at, bodyFingerprint(body)),
        )
    }

    /**
     * 保留策略。表只增不减的话，「没认出来的取件短信」迟早变成一个没人打开的 archive。
     * 表态满 [decidedTtlMs] 的丢掉；还在看的按时间留最新 [newCap] 条。
     */
    fun pruneUnrecognized(now: Long, decidedTtlMs: Long, newCap: Int) {
        writableDatabase.execSQL(
            "DELETE FROM unrecognized WHERE state != 'NEW' AND decided_at IS NOT NULL AND decided_at < ?",
            arrayOf<Any>(now - decidedTtlMs),
        )
        // LIMIT 待在子查询里：Android 自带的 SQLite 没开 UPDATE/DELETE-LIMIT 编译开关，
        // 直接写 "DELETE ... LIMIT n" 会当场报语法错。
        writableDatabase.execSQL(
            "DELETE FROM unrecognized WHERE state = 'NEW' AND id NOT IN" +
                " (SELECT id FROM unrecognized WHERE state = 'NEW' ORDER BY received_at DESC LIMIT ?)",
            arrayOf<Any>(newCap),
        )
    }

    // ---------- 其它 ----------

    fun allAliases(): List<PointAlias> = readableDatabase
        .rawQuery("SELECT alias_key, alias_name, target_key, target_name, created_at FROM point_alias", null)
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        PointAlias(
                            aliasKey = c.getString(0),
                            aliasName = c.getString(1),
                            targetKey = c.getString(2),
                            targetName = c.getString(3),
                            createdAt = c.getLong(4),
                        )
                    )
                }
            }
        }

    fun putAlias(alias: PointAlias) {
        writableDatabase.insertWithOnConflict(
            "point_alias",
            null,
            ContentValues().apply {
                put("alias_key", alias.aliasKey)
                put("alias_name", alias.aliasName)
                put("target_key", alias.targetKey)
                put("target_name", alias.targetName)
                put("created_at", alias.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteAlias(aliasKey: String) {
        writableDatabase.delete("point_alias", "alias_key = ?", arrayOf(aliasKey))
    }

    fun allDismissals(): Set<String> = readableDatabase
        .rawQuery("SELECT pair_key FROM point_dismiss", null)
        .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) }.toSet() }

    fun putDismissal(pairKey: String, at: Long) {
        writableDatabase.insertWithOnConflict(
            "point_dismiss",
            null,
            ContentValues().apply { put("pair_key", pairKey); put("created_at", at) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---------- 解析规则 ----------

    fun allRules(): List<ParseRule> = readableDatabase
        .rawQuery(
            "SELECT id, pattern, point_name, locker_no, enabled, created_at FROM parse_rule" +
                " ORDER BY created_at DESC",
            null,
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ParseRule(
                            id = c.getLong(0),
                            pattern = c.getString(1),
                            pointName = c.getString(2),
                            lockerNo = if (c.isNull(3)) null else c.getString(3),
                            enabled = c.getInt(4) != 0,
                            createdAt = c.getLong(5),
                        )
                    )
                }
            }
        }

    /** 和 [insertParcel] 同一条规矩：撞约束只返回 -1 不抛，调用方必须查返回值。 */
    fun insertRule(pattern: String, pointName: String, lockerNo: String?, at: Long): Long =
        writableDatabase.insert(
            "parse_rule",
            null,
            ContentValues().apply {
                put("pattern", pattern)
                put("point_name", pointName)
                put("locker_no", lockerNo)
                put("enabled", 1)
                put("created_at", at)
            },
        )

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        writableDatabase.update(
            "parse_rule",
            ContentValues().apply { put("enabled", if (enabled) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("parse_rule", "id = ?", arrayOf(id.toString()))
    }

    /**
     * 还挂着的未识别正文，按时间从新到旧，最多 [limit] 条。
     *
     * 只取重判要用的那三个字段：这一步是「用户刚写完一条规则」触发的，
     * 不是后台常驻，所以要把上界钉死——库里攒了几百条时不该一次全跑。
     */
    fun newUnrecognizedBodies(limit: Int): List<UnrecognizedRecord> = readableDatabase
        .rawQuery(
            "SELECT id, sender, body, received_at FROM unrecognized WHERE state = 'NEW'" +
                " ORDER BY received_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(UnrecognizedRecord(id = c.getLong(0), sender = c.getString(1), body = c.getString(2), receivedAt = c.getLong(3)))
                }
            }
        }

    /** 规则救回了这条正文：表态成 RESOLVED，和手动提取同一个语义。 */
    fun resolveUnrecognizedByIds(ids: List<Long>, at: Long) {
        if (ids.isEmpty()) return
        val args = ArrayList<Any?>(ids.size + 1).apply { add(at); addAll(ids) }.toTypedArray()
        writableDatabase.execSQL(
            "UPDATE unrecognized SET state = 'RESOLVED', decided_at = ? WHERE id IN (${ids.joinToString(",") { "?" }})",
            args,
        )
    }

    fun metaGet(key: String): String? = readableDatabase
        .rawQuery("SELECT mvalue FROM meta WHERE mkey = ?", arrayOf(key))
        .use { if (it.moveToFirst()) it.getString(0) else null }

    fun metaPut(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "meta",
            null,
            ContentValues().apply { put("mkey", key); put("mvalue", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun countPickedSince(since: Long): Int = readableDatabase
        .rawQuery("SELECT COUNT(*) FROM parcel WHERE picked = 1 AND picked_at >= ?", arrayOf(since.toString()))
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun lastPickupAt(): Long? = readableDatabase
        .rawQuery("SELECT MAX(picked_at) FROM parcel WHERE picked = 1", null)
        .use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    private fun SQLiteDatabase.queryAll(): Cursor = rawQuery(
        "SELECT id, code, locker_no, carrier, point_key, point_name, point_kind, owner, source," +
            " received_at, free_until_at, closes_at_minute, picked, picked_at, raw" +
            " FROM parcel ORDER BY picked ASC, received_at DESC",
        null,
    )

    private fun Cursor.toParcel() = Parcel(
        id = getLong(0),
        code = getString(1),
        lockerNo = if (isNull(2)) null else getString(2),
        carrier = getString(3),
        pointKey = getString(4),
        pointName = getString(5),
        // 认不出类型时退成驿站：柜件的 closes_at_minute 那列是 null，于是 deadline 为空 → 安全态。
        // 宁可少一个颜色，也不编造一个截止时间把人吓得半夜跑一趟。
        pointKind = enumOrDefault(getString(6), PointKind.STATION),
        owner = enumOrDefault(getString(7), Owner.SELF),
        source = enumOrDefault(getString(8), Source.SMS),
        receivedAt = getLong(9),
        freeUntilAt = if (isNull(10)) null else getLong(10),
        closesAtMinuteOfDay = if (isNull(11)) null else getInt(11),
        picked = getInt(12) != 0,
        pickedAt = if (isNull(13)) null else getLong(13),
        raw = getString(14),
    )

    private companion object {
        const val NAME = "showcode.db"
        const val VERSION = 4
    }
}
