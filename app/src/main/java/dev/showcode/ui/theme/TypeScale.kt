package dev.showcode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * tabular-nums。Material3 1.4 的 Text 没有 fontFeatureSettings 形参，只能从 style 走。
 * 取件码和倒计时都要它：数字逐秒跳动时如果每个数字宽度不同，整行会左右晃，
 * 而在柜机键盘前一位一位念码的人最怕的就是这个。
 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * 让任何一个字阶 role 保住等宽数字。
 *
 * 迁移到 [ShowCodeText] 之前，这些站点是手写 `style = TabularNumbers` 的；
 * role 一旦接管 style，不显式带上 tnum 就会把它丢掉——而「数字逐秒跳动时整行不许左右晃」
 * 是现场与首页共用的契约，不能因为换了一套字阶就失效。
 */
fun TextStyle.withTabular() = copy(fontFeatureSettings = "tnum")

private fun tabular() = TextStyle.Default.copy(fontFeatureSettings = "tnum")

/**
 * 管理模式的字阶。
 *
 * 立它的理由不是「好看」：改之前管理模式有 126 处 `fontSize = N.sp`、14 个不同档位，
 * 而 `MaterialTheme.typography` 全 App 被消费 **0** 次；64 处字重里 Normal 只有 2 处，
 * 也就是说屏上几乎每一行都是粗体。**全都加粗 = 没有任何一行突出**，这和「颜色没层次」
 * 是同一个病的两面。所以这里的收敛目标是两件事：档位变少，正文回到常规字重。
 *
 * 字重只允许 400 / 700 / 800 三档（原来的 SemiBold 并进 700）。800 只留给件数和屏标题，
 * 这样「很重」重新变成一个有信息量的信号。
 *
 * 现场模式不从这里取值——那一屏的字号由 CodeFontSize 按码长算出来，契约是 21:1。
 */
object ShowCodeText {
    /** 状态角标：临期/过期风险这种两三个字的标签。 */
    val Micro = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)

    /** 辅助文字：单位、柜号、时间戳。 */
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp)

    /** 说明与次要正文，管理模式里用量最大的一档。 */
    val Meta = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)

    /** 行内可点动作：去开启 / 撤销 / 查看。字号同 Meta，靠字重区分「这是按钮」。 */
    val Action = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)

    /** 正文。 */
    val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)

    /** 条目主文字：卡里的一行、列表项的标题。 */
    val Item = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)

    /** 区块标题。 */
    val Section = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)

    /**
     * 引导页的装饰档。emoji 不是文字，它要的是「占多大一块图形」而不是排版层级；
     * 单独立两档，是为了让管理模式不再有任何裸 fontSize。
     */
    val Display = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp)

    /** 示例数据的水印。同样不是排版层级，是压在卡面上的一层图形。 */
    val Watermark = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.15.em)

    /** 屏标题。 */
    val Headline = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)

    /** 取件点卡上的件数。必须 tabular，数字变化时整行不许左右晃。 */
    val Count = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp) + tabular()

    /** 卡片里的取件码本体：等宽 + tabular。 */
    val Code = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) + tabular()

    /**
     * 同一条 AC 的另外两端：现场模式「没有柜号时那一整块消失、码变得更大」，
     * 而「最近已取」是误触补救出口，码要比主路径小一档。三档共用等宽 + tabular。
     */
    val CodeLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) + tabular()
    val CodeSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) + tabular()
}

/**
 * 形状阶。原来是 10 个不同的圆角裸值（2/4/5/6/8/10/12/13/14/16），
 * 只有两档：全 App 没有第三类需要更大圆角的表面，留一个没人消费的档
 * 就是「Material 给了 5 个容器 role、代码只消费 0 个」那种死配置。
 * Pill 不算字阶的一档：色条和徽章要的是胶囊，不是「更大的圆角」。
 */
object ShowCodeShapes {
    val Sm = RoundedCornerShape(8.dp)
    val Md = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * 把 [ShowCodeText] 灌进 M3 的 role 槽位。
 *
 * 不这么做的后果是组件默认样式继续吃 M3 的基线 typography，而 M3 1.4 的基线是
 * 按拉丁文排版优化的——中文在 labelSmall 那档（11sp）会显得比视觉上更小。
 * 这里让默认 role 也落到本 App 的阶上，任何没被迁移到的组件都不会跑偏。
 */
@Composable
fun showCodeTypography(): Typography = Typography().copy(
    labelSmall = ShowCodeText.Micro,
    labelMedium = ShowCodeText.Meta,
    labelLarge = ShowCodeText.Action,
    bodySmall = ShowCodeText.Caption,
    bodyMedium = ShowCodeText.Body,
    bodyLarge = ShowCodeText.Item,
    titleSmall = ShowCodeText.Item,
    titleMedium = ShowCodeText.Section,
    titleLarge = ShowCodeText.Headline,
    headlineSmall = ShowCodeText.Headline,
    headlineMedium = ShowCodeText.Headline,
    headlineLarge = ShowCodeText.Headline,
    displaySmall = ShowCodeText.Count,
    displayMedium = ShowCodeText.Count,
    displayLarge = ShowCodeText.Headline,
)
