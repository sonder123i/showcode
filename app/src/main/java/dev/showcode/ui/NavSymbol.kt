package dev.showcode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.sin

/**
 * 底栏三枚字形：待取件（包裹）/ 身份码（二维码）/ 设置（滑杆）。
 *
 * 画法照教务 `PhosphorNavigationIcon` 的做法：256 网格、16f 圆头描边、
 * **双层 duotone**（填充层 α = `0.08 + 0.16·weight`，描边层用 tint 本色），
 * 动效状态一点不进渲染器——所有采样副本画的是同一帧。
 *
 * ⚠️ [modifier] 必须带**尺寸**（教务调用点传的是 `Modifier.size(24.dp)`）：
 * Canvas 是靠 modifier 撑开的，不给尺寸在 Box 里会量成 0×0、什么也不画。
 *
 * 字形来源说明：设置那枚与教务同几何（Phosphor sliders 系，MIT）；
 * 另外两枚按本仓库既有线稿语义（箱子 / 二维码）在同网格上重画成 duotone。
 * 动效按家族的 `flourish` 语法定制：一次正向动作 + 一次更小的回弹，两端严格归零。
 */
enum class NavSymbol { PICKUP, IDENTITY, SETTINGS }

@Composable
fun NavSymbolIcon(
    symbol: NavSymbol,
    selection: Float,
    phase: () -> Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val t = phase().coerceIn(0f, 1f)
        val weight = selection.coerceIn(0f, 1f)
        val fill = tint.copy(alpha = tint.alpha * (0.08f + 0.16f * weight))
        scale(size.width / 256f, size.height / 256f, Offset.Zero) {
            when (symbol) {
                NavSymbol.PICKUP -> drawPackage(t, tint, fill)
                NavSymbol.IDENTITY -> drawQr(t, tint, fill)
                NavSymbol.SETTINGS -> drawSliders(t, tint, fill)
            }
        }
    }
}

private val outlineStroke = Stroke(16f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** 一次正向动作 + 更小的回弹，两端严格归零（教务 flourish 原式）。 */
private fun flourish(t: Float): Float = sin(PI * t).toFloat() * (1f - 0.8f * t) +
    sin(3.0 * PI * t).toFloat() * 0.18f

/** 闪烁亮度：经过时亮一下再暗回去，两端为 0。 */
private fun light(t: Float): Float = sin(2.0 * PI * t).toFloat() * sin(PI * t).toFloat()

/**
 * 待取件：箱子。箱盖绕盖缝抬起一点再落回，箱体反向微倾——
 * 「盖子掀开看一眼」的动作，比原地放大更有话说。
 */
private fun DrawScope.drawPackage(t: Float, tint: Color, fill: Color) {
    val move = flourish(t)
    rotate(-5f * move, Offset(128f, 216f)) {
        drawRect(fill, Offset(48f, 88f), Size(160f, 128f))
        val body = Path().apply {
            moveTo(48f, 88f)
            lineTo(208f, 88f)
            lineTo(208f, 216f)
            lineTo(48f, 216f)
            close()
        }
        drawPath(body, tint, style = outlineStroke)
        drawLine(tint, Offset(128f, 88f), Offset(128f, 216f), 16f)
        drawCircle(tint, 15f, Offset(128f, 56f))
    }
    translate(0f, -22f * move) {
        rotate(-9f * move, Offset(128f, 88f)) {
            drawLine(tint, Offset(28f, 88f), Offset(228f, 88f), 16f, StrokeCap.Round)
        }
    }
}

/**
 * 身份码：二维码。三个定位角 + 右下模块群，一道扫描带自上而下扫过，
 * 路上的模块逐个点亮——「扫一下」是这一屏的全部用途。
 */
private fun DrawScope.drawQr(t: Float, tint: Color, fill: Color) {
    fun finder(x: Float, y: Float) {
        drawRect(fill, Offset(x, y), Size(64f, 64f))
        drawRoundRect(tint, Offset(x, y), Size(64f, 64f), CornerRadius(12f), style = outlineStroke)
        drawRect(tint, Offset(x + 24f, y + 24f), Size(16f, 16f))
    }
    finder(40f, 40f)
    finder(152f, 40f)
    finder(40f, 152f)
    val modules = listOf(
        Offset(160f, 160f),
        Offset(196f, 160f),
        Offset(160f, 196f),
        Offset(196f, 196f),
        Offset(124f, 124f),
    )
    modules.forEachIndexed { index, p ->
        val dotT = ((t - index * 0.08f) / 0.55f).coerceIn(0f, 1f)
        val l = light(dotT)
        drawRect(
            tint.copy(alpha = tint.alpha * (0.55f + 0.45f * l.coerceAtLeast(0f))),
            p,
            Size(24f + 6f * l.coerceAtLeast(0f), 24f + 6f * l.coerceAtLeast(0f)),
        )
    }
    val sweep = (t / 0.70f).coerceIn(0f, 1f)
    val y = 32f + 192f * sweep
    clipRect(24f, 24f, 232f, 232f) {
        drawRoundRect(
            tint.copy(alpha = tint.alpha * 0.35f * sin(PI * t).toFloat()),
            Offset(24f, y - 11f),
            Size(208f, 22f),
            CornerRadius(11f),
        )
    }
}

/**
 * 设置：滑杆。与教务 `drawSliders` 同几何同动效（Phosphor sliders 系，MIT）——
 * 这一枚不改的理由：同一个字形在两个 App 里就该一模一样，家族感正是这么来的。
 */
private fun DrawScope.drawSliders(t: Float, tint: Color, fill: Color) {
    val shift = 54f * flourish(t)
    for ((x, y) in listOf(104f + shift to 80f, 168f - shift to 176f)) {
        drawLine(tint, Offset(40f, y), Offset(x - 24f, y), 16f, StrokeCap.Round)
        drawLine(tint, Offset(x + 24f, y), Offset(216f, y), 16f, StrokeCap.Round)
        drawCircle(fill, 24f, Offset(x, y))
        drawCircle(tint, 24f, Offset(x, y), style = outlineStroke)
    }
}
