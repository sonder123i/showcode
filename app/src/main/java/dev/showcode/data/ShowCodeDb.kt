package dev.showcode.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointAlias
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.enumOrDefault
import dev.showcode.core.parse.UnrecognizedReason

/** 一条没认出来的取件消息。原文必须留着——用户要靠原文判断自己漏了什么。 */
data class UnrecognizedMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val reason: UnrecognizedReason,
    val receivedAt: Long,
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
        db.execSQL("CREATE UNIQUE INDEX parcel_dedupe ON parcel(code, point_key) WHERE picked = 0")
        db.execSQL("CREATE INDEX parcel_point ON parcel(point_key, picked)")
        db.execSQL(
            """
            CREATE TABLE unrecognized (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              sender TEXT NOT NULL,
              body TEXT NOT NULL,
              reason TEXT NOT NULL,
              received_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE meta (mkey TEXT PRIMARY KEY, mvalue TEXT NOT NULL)")
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
    }

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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：story-6 的两张表是纯新增，旧库里没有，直接补建即可。
        // 这里绝不能走「删表重建」——那会把用户已经收进来的取件码全清掉。
        if (oldVersion < 2) {
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
    }

    fun allParcels(): List<Parcel> = readableDatabase.queryAll().use { c -> buildList { while (c.moveToNext()) add(c.toParcel()) } }

    fun findUnpicked(code: String, pointKey: String): Long? = readableDatabase.rawQuery(
        "SELECT id FROM parcel WHERE code = ? AND point_key = ? AND picked = 0 LIMIT 1",
        arrayOf(code, pointKey),
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    /**
     * 插入新行。`SQLiteDatabase.insert()` 撞约束时**返回 -1 而不是抛**，
     * 所以返回值 ≤ 0 就是「没写进去」。调用方必须检查——不检查的话界面会跳到首页
     * 像是存好了，而那个码根本不在库里。
     */
    fun insertParcel(values: ContentValues): Long = writableDatabase.insert("parcel", null, values)

    fun updateParcel(id: Long, values: ContentValues) {
        writableDatabase.update("parcel", values, "id = ?", arrayOf(id.toString()))
    }

    fun allUnrecognized(): List<UnrecognizedMessage> = readableDatabase
        .rawQuery("SELECT id, sender, body, reason, received_at FROM unrecognized ORDER BY received_at DESC", null)
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
                        )
                    )
                }
            }
        }

    fun hasUnrecognized(sender: String, body: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM unrecognized WHERE sender = ? AND body = ? LIMIT 1",
        arrayOf(sender, body),
    ).use { it.moveToFirst() }

    fun insertUnrecognized(values: ContentValues) {
        writableDatabase.insert("unrecognized", null, values)
    }

    fun deleteUnrecognized(id: Long) {
        writableDatabase.delete("unrecognized", "id = ?", arrayOf(id.toString()))
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
        const val VERSION = 2
    }
}
