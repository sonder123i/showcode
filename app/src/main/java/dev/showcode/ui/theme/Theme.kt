package dev.showcode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 管理模式的色阶。
 *
 * ⚠️ 这一张表存在的首要理由是**防紫**。M3 的基线 tone 面板整体带淡紫偏色，而本 App
 * 只有黑白 + 三态色；这些 role 不显式写就会漏进组件默认色——设置页滑杆的未填充段
 * 实测是 #E8DEF8，正是 secondaryContainer 的默认值（2026-10-13 从截图采样 3146 个像素确认）。
 *
 * 那次修复顺手把五个 surfaceContainer* 全压成了同一个 #F1F1F1，代价是首页的层次也被一起
 * 压平：卡片和白页的亮度差是 0，分离只剩一根半透明描边。现在把这一族**重新分档**，
 * 但每个值仍然显式写出——「不为没人消费的 role 新开色阶」这条原则不变，
 * 变的是那些 role 现在真的有人消费了。
 *
 * 每个中性值都必须满足 R ≤ G ≤ B（纯灰或冷调，绝不允许 R > G 的紫/品红偏色）。
 * 这条由 NeutralRampTest 断言守住，它同时放过 #E4E8F0 这类合法冷灰、
 * 拒掉 #E8DEF8 / #F3EDF7 / #E7E0EC 这三个 M3 默认值。
 */
private val ShowCodeColors = lightColorScheme(
    /* 现场模式的两端不进这张表：primary 仍指纯黑，那一屏要的是 21:1。 */
    primary = OnsiteInk,
    onPrimary = OnsiteSurface,
    secondary = Accent,
    onSecondary = OnsiteSurface,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,

    surfaceVariant = SurfaceSunken,
    onSurfaceVariant = InkSecondary,
    outline = Hairline,
    outlineVariant = Hairline,

    /* 三档容器：Low/Container 是卡面，High 是卡里的内嵌块，Highest 是最深的凹槽。 */
    surfaceContainerLowest = Canvas,
    surfaceContainerLow = SurfaceCard,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceSunken,
    surfaceContainerHighest = SurfaceDeep,
    surfaceBright = Canvas,
    surfaceDim = SurfaceCard,

    /* 曾经的紫偏源头。给同族冷调的浅蓝而不是灰——它是 Accent 的容器色，不是中性面。 */
    secondaryContainer = AccentTint,
    onSecondaryContainer = Ink,
    tertiary = Accent,
    onTertiary = OnsiteSurface,
    tertiaryContainer = AccentTint,
    onTertiaryContainer = Ink,

    inverseSurface = OnsiteInk,
    inverseOnSurface = OnsiteSurface,
    error = UrgencyAtRisk,
    /* errorContainer 不列——它是红系，和三态色同族，不是漏进来的紫。 */
)

/**
 * 强制浅色，不跟随系统深色模式。
 * 理由不是审美：现场模式要在直射阳光下做到 21:1，深色模式会把它翻成白字黑底，
 * 而强光下深色底的反光会让取件码读不出来——那是这个产品唯一不能妥协的一屏。
 */
@Composable
fun ShowCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShowCodeColors,
        typography = showCodeTypography(),
        content = content,
    )
}
