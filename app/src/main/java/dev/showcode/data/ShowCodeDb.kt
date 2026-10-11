package dev.showcode.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 尚无发布版本，schema 变更直接重建。发布后必须改成迁移。
        db.execSQL("DROP TABLE IF EXISTS parcel")
        db.execSQL("DROP TABLE IF EXISTS unrecognized")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    fun allParcels(): List<Parcel> = readableDatabase.queryAll().use { c -> buildList { while (c.moveToNext()) add(c.toParcel()) } }

    fun findUnpicked(code: String, pointKey: String): Long? = readableDatabase.rawQuery(
        "SELECT id FROM parcel WHERE code = ? AND point_key = ? AND picked = 0 LIMIT 1",
        arrayOf(code, pointKey),
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

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
                            reason = runCatching { UnrecognizedReason.valueOf(c.getString(3)) }
                                .getOrDefault(UnrecognizedReason.NO_CODE_FOUND),
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
        pointKind = PointKind.valueOf(getString(6)),
        owner = Owner.valueOf(getString(7)),
        source = Source.valueOf(getString(8)),
        receivedAt = getLong(9),
        freeUntilAt = if (isNull(10)) null else getLong(10),
        closesAtMinuteOfDay = if (isNull(11)) null else getInt(11),
        picked = getInt(12) != 0,
        pickedAt = if (isNull(13)) null else getLong(13),
        raw = getString(14),
    )

    private companion object {
        const val NAME = "showcode.db"
        const val VERSION = 1
    }
}
