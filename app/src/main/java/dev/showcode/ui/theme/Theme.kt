package dev.showcode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/**
 * tabular-nums。Material3 1.4 的 Text 没有 fontFeatureSettings 形参，只能从 style 走。
 * 取件码和倒计时都要它：数字逐秒跳动时如果每个数字宽度不同，整行会左右晃，
 * 而在柜机键盘前一位一位念码的人最怕的就是这个。
 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

private val ShowCodeColors = lightColorScheme(
    primary = OnsiteInk,
    onPrimary = OnsiteSurface,
    secondary = Accent,
    onSecondary = OnsiteSurface,
    background = OnsiteSurface,
    onBackground = OnsiteInk,
    surface = OnsiteSurface,
    onSurface = OnsiteInk,
    surfaceVariant = SurfaceInset,
    onSurfaceVariant = OnsiteInk,
    outline = Hairline,
    outlineVariant = Hairline,
    error = UrgencyAtRisk,
)

/**
 * 强制浅色，不跟随系统深色模式。
 * 理由不是审美：现场模式要在直射阳光下做到 21:1，深色模式会把它翻成白字黑底，
 * 而强光下深色底的反光会让取件码读不出来——那是这个产品唯一不能妥协的一屏。
 */
@Composable
fun ShowCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ShowCodeColors, content = content)
}
