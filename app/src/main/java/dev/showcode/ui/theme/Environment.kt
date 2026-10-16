package dev.showcode.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 环境层：全 App 唯一的浅色底，**逐字移植教务 `WallpaperPreset.Aurora`（流光）**，
 * 绘制顺序与几何也照抄 `ui/system/WallpaperRenderer.kt` 的 `drawWallpaperPattern`：
 *
 *     rect(base)
 *     circle(radial white @0.72, center (0.18w, 0.12h), r 0.72w)          ← 左上高光
 *     for a in accents: circle(radial a @alpha, center (cx·w, cy·h), r radius·w)
 *     circle(radial shade @0.09, center (0.78w, 0.88h), r 0.64w)          ← 右下暗角
 *
 * 2026-10-16 更正：上一版是**四个合成像素的对角线性渐变**，两头都错——一是那四个角在教务源码里
 * 根本不存在（是 Aurora 四个 accent 被底色冲淡后的合成值），二是强度做平了 **5 倍**：
 * 我采到的四角跨度 0.046，而这套配方的实际跨度是 0.247。这版按机制重画。
 *
 * ⚠️ 与家族唯一的偏离是**一个数**：暗角 alpha `0.12 → 0.09`。理由是对比度——满了会让环境最暗点
 * 落到 L 0.694，`InkMuted #59616F` 与教务自己的 `#0057D9` 在那里都只剩 4.42:1（破 AA）；
 * 收到 0.09 之后最暗点是 0.719，两个都过 4.57:1。高光 0.72 与四个 accent 的 alpha **一个没动**。
 * 这条判据由 `NeutralRampTest` 钉着（对着 [EnvironmentDarkest] 算），脚本在 `.verify/env-check.py`。
 *
 * 现场模式（`ui/onsite/`）**不铺这一层**：那三屏的契约是纯黑 on 纯白 ≈ 21:1。
 */
private val EnvBase = Color(0xFFF6F6FB)
private val EnvGlow = Color(0xFFFFFFFF)
private val EnvShade = Color(0xFF5A6B85)

private const val GlowAlpha = 0.72f
private const val ShadeAlpha = 0.09f

private class EnvAccent(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val alpha: Float,
)

/** 四个 accent 的位置、半径、alpha 与教务预设表里的值完全一致。 */
private val EnvAccents = listOf(
    EnvAccent(Color(0xFF9BC4F8), 0.15f, 0.18f, 0.58f, 0.42f),
    EnvAccent(Color(0xFFC3B3F2), 0.88f, 0.32f, 0.62f, 0.38f),
    EnvAccent(Color(0xFF9BE5DE), 0.26f, 0.64f, 0.56f, 0.34f),
    EnvAccent(Color(0xFFF4B8D4), 0.80f, 0.88f, 0.60f, 0.32f),
)

/**
 * 环境层最暗那一点（右下暗角中心，也是粉色 accent 的峰值处）的合成值。
 * 墨阶的 AA 判据必须对着它算，不能对着纯白算——脚本算得 L=0.7192。
 *
 * 只叠「base + 粉色 accent + 暗角」：那一点上另外三个 accent 与高光都够不着（衰减到 0），
 * 所以这是一个**上界**而不是精确值。假设写在注释里，只有它变了才需要重算。
 */
val EnvironmentDarkest: Color = compositeOver(
    compositeOver(EnvBase, EnvAccents[3].color, EnvAccents[3].alpha),
    EnvShade,
    ShadeAlpha,
)

/**
 * **紧挨卡片的**页面最亮值。
 *
 * 环境层的全局最亮点在顶部（0.18w, 0.12h 那道白光，L 0.9412），而那一带是页眉/状态栏、
 * 没有卡片——拿它当"卡面浮不浮得起来"的对手是错的对手。真正的约束是**卡片旁边那片页面**：
 * 左右各 3% 宽的边条、y 取 0.15h~0.85h，那里最亮是 #F0F5F9（L 0.9067），卡面在它之上 ΔL 0.049。
 * 若哪天有卡片挪到页眉位置，这条就会失效（那 ΔL 只剩 0.0148）——脚本里留着这个数备查。
 */
val EnvironmentCardEdgeLightest: Color = Color(0xFFF0F5F9)

private fun compositeOver(bottom: Color, top: Color, alpha: Float): Color = Color(
    red = top.red * alpha + bottom.red * (1f - alpha),
    green = top.green * alpha + bottom.green * (1f - alpha),
    blue = top.blue * alpha + bottom.blue * (1f - alpha),
)

/** 画一遍环境层。与教务同一顺序：底 → 高光 → 光斑 → 暗角。 */
fun DrawScope.drawEnvironment() {
    val w = size.width
    val h = size.height
    drawRect(EnvBase)

    val glowCenter = Offset(w * 0.18f, h * 0.12f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(EnvGlow.copy(alpha = GlowAlpha), Color.Transparent),
            center = glowCenter,
            radius = w * 0.72f,
        ),
        radius = w * 0.72f,
        center = glowCenter,
    )

    EnvAccents.forEach { accent ->
        val center = Offset(w * accent.cx, h * accent.cy)
        val radius = w * accent.radius
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.color.copy(alpha = accent.alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    val shadeCenter = Offset(w * 0.78f, h * 0.88f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(EnvShade.copy(alpha = ShadeAlpha), Color.Transparent),
            center = shadeCenter,
            radius = w * 0.64f,
        ),
        radius = w * 0.64f,
        center = shadeCenter,
    )
}

/**
 * 各屏根容器的环境底。用 `drawBehind` 而不是 `background(brush)`：这套配方是多层径向叠加，
 * 没有单一 Brush 能表达。铺在 `windowInsetsPadding(systemBars)` **之前**，好盖住状态栏与手势区。
 */
fun Modifier.environmentBackground(): Modifier = drawBehind { drawEnvironment() }