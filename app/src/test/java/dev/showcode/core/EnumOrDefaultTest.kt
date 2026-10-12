package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 读库时枚举对不上名字的那条退路。
 *
 * 这条路径以前是裸 `valueOf`：只要以后给 PointKind / Owner / Source 改过一次名，
 * 或者用户装了更旧的 APK，`reload()` 每次读表都会抛，整个 App 打不开——
 * 一列文本决定了用户能不能看见自己的取件码，这个代价不成比例。
 */
class EnumOrDefaultTest {

    @Test
    fun `认识的枚举值照常解出来`() {
        assertEquals(PointKind.LOCKER, enumOrDefault("LOCKER", PointKind.STATION))
        assertEquals(Owner.FAMILY, enumOrDefault("FAMILY", Owner.SELF))
        assertEquals(Source.NOTIFICATION, enumOrDefault("NOTIFICATION", Source.SMS))
    }

    @Test
    fun `改了名或更旧 APK 写进来的值退成默认而不是抛`() {
        // 名字不存在：以前这里直接 IllegalArgumentException。
        assertEquals(PointKind.STATION, enumOrDefault("LOCKER_V2", PointKind.STATION))
        assertEquals(Owner.SELF, enumOrDefault("BORROWED", Owner.SELF))
        // 大小写也算对不上——归一化不是这个函数的职责，它只保证不炸。
        assertEquals(Source.SMS, enumOrDefault("sms", Source.SMS))
    }

    @Test
    fun `空列退成默认`() {
        assertEquals(PointKind.STATION, enumOrDefault(null, PointKind.STATION))
    }
}
