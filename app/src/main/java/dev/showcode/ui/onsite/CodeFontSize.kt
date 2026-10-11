package dev.showcode.ui.onsite

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 取件码字号。与 H5 参照 `showcode/src/flows/flow-1/flow1-onsite-present.tsx` 的
 * `codeFontSize()` 一一对应：
 *
 *     base = len > 8 ? 54 : len > 6 ? 64 : 72
 *     px   = hasLocker ? base : round(base * 1.15)   // 无柜号放大 15%：不是留白，是重排
 *     size = min(px, (140 / len) vw)                 // 防溢出不变量
 *
 * 140 这个常数已经把两侧各 20 的内边距算进去了（在 320 视口下占掉 12.5%），
 * 于是 8 字符码在 320dp 宽的屏上自动缩到 56（码宽 8×0.62×56≈278dp ≤ 280dp 可用），
 * 在更宽的屏上才走 base。320dp 正是 iPhone SE 与大量低端安卓机的宽度。
 *
 * Compose 侧唯一的差别，也是最容易做错的一处：CSS 的 px **不**跟随系统字体缩放，sp 会。
 * 所以做法是先在 dp 空间里算出「实际渲染高度」，再除以 fontScale 换算回 sp：
 *  - fontScale = 1 时结果与 CSS 完全相同；
 *  - fontScale > 1 时 base 可以随用户偏好放大，但被 vw 上限按死在可用宽度内。
 * 直接把 vw 上限除以 fontScale 是错的——那样 base 胜出时照样会溢出
 * （6 字符码在 320dp、1.3 倍字体下会渲染成 300dp 宽 > 280dp 可用），
 * 而这个不变量正是 mockup 的 Check finding M-r2-3 要守的东西。
 */
fun codeFontSizeSp(code: String, hasLocker: Boolean, viewportWidthDp: Float, fontScale: Float): Float {
    val len = code.length.coerceAtLeast(1)
    val scale = fontScale.coerceAtLeast(0.1f)
    val base = when {
        len > 8 -> 54f
        len > 6 -> 64f
        else -> 72f
    }
    val targetDp = if (hasLocker) base else (base * 1.15f).roundToInt().toFloat()
    val capDp = 140f / len * viewportWidthDp / 100f
    return min(targetDp * scale, capDp) / scale
}

fun codeFontSize(code: String, hasLocker: Boolean, viewportWidthDp: Float, fontScale: Float): TextUnit =
    codeFontSizeSp(code, hasLocker, viewportWidthDp, fontScale).sp
