package dev.showcode.core.qr

import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment

/**
 * 二维码的模块矩阵——**这张屏上最不能出错的东西是「扫不出来」，不是「不好看」**。
 *
 * 它是 `io.nayuki.qrcodegen` 的一层薄壳，存在的理由有两条，都不是为了好看：
 *
 * 1. **纯 JVM、可单测。** 生成器本身是纯计算，但它返回的是 `getModule(x, y)` 这种
 *    要走对象图的东西，而 Android 的 `Bitmap` 一旦进来，这一层在单测里就摸不到了。
 *    所以矩阵在这里落地成值，`ui/` 那一侧只管把布尔画成方块。
 * 2. **版本上限是判据，不是实现细节。** 见 [MAX_VERSION]。
 */
data class QrMatrix(
    /** 二维码版本号，1..[MAX_VERSION]。边长 = 17 + 4×版本，版本也是「这个码有多密」的唯一读数。 */
    val version: Int,
    val size: Int,
    val rows: List<List<Boolean>>,
) {

    /** 画一屏二维码的总边长（含四周静区）。静区不是留白：扫码器要靠它判断码从哪结束。 */
    val sizeWithQuietZone: Int get() = size + QUIET_ZONE_MODULES * 2

    fun isDark(x: Int, y: Int): Boolean = rows[y][x]
}

/** 规范要求的四周静区（模块数）。低于 2 个模块规范就不保证可扫，4 是标配。 */
const val QUIET_ZONE_MODULES = 4

/**
 * 允许的最高二维码版本。版本 N 的边长是 `17 + 4N` 个模块。
 *
 * 卡在 15（77×77）而不是让生成器自己往 40 走，理由全在**物理**那一侧：
 * 手机屏不是打印纸。一块 1080px 宽的手机屏画 77 模块，每个模块只剩约 14 物理像素；
 * 到版本 25（117 模块）就只剩 9 个，加上屏反光与摩尔纹，柜机那个摄像头拍到的是糊的。
 * 而用户是站在柜机前发现这件事的——那正是本 App 的红线。
 *
 * 真有多大？15 版 ECC-M 的容量是 666 个数字 / 236 个字节，身份码这类短串远够不着。
 * 真撞上限的一定是一条长 URL，那种东西本来就不该印成码给人扫，该走深链。
 */
const val MAX_VERSION = 15

/**
 * 把一段文本编成模块矩阵；**编不出来返回 null**，不抛。
 *
 * 不抛是刻意的：这一条会被「保存前那一眼」调用（[dev.showcode.core.IdentityCodes.validate]），
 * 装不下是一种**要告诉人的结果**，不是一次崩溃。
 */
object Qr {

    /**
     * ECC 用 M、允许 boost。
     *
     * 纠错等级越高越耐脏，但同样的内容要塞进更高的版本=更多模块=每个模块在屏上更小，
     * 而手机屏上的模块尺寸正是瓶颈——所以 H/Q 在这一层是**反向**的。
     * M（约 15％ 冗余）是「拍糊一点也认得出」和「模块别小于 10 像素」之间的那一格。
     */
    fun of(value: String): QrMatrix? {
        if (value.isEmpty()) return null
        val code = runCatching {
            QrCode.encodeSegments(
                QrSegment.makeSegments(value),
                QrCode.Ecc.MEDIUM,
                QrCode.MIN_VERSION,
                MAX_VERSION,
                -1,
                true,
            )
        }.getOrNull() ?: return null
        val size = code.size
        return QrMatrix(
            version = code.version,
            size = size,
            rows = List(size) { y -> List(size) { x -> code.getModule(x, y) } },
        )
    }
}
