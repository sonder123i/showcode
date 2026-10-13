package dev.showcode.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 现场模式的唯一使用环境是户外强光，对比度必须拉满：纯黑 on 纯白 ≈ 21:1。
 * 刻意不用 Material 的 onSurface（那通常是 #1C1B1F 之类，直射阳光下会发灰）。
 *
 * 这两个值是**现场模式专属契约**，管理模式不得借用——借用会让首页继承一个
 * 为 21:1 设计的纯黑，然后把首页的文字层级全部压成「黑 + 手调 alpha」。
 */
val OnsiteInk = Color.Black
val OnsiteSurface = Color.White

/**
 * 三态配色。frame.json 锁定「视觉只有三态三色」——紧迫度梯度靠倒计时数字连续变化承载，
 * 不加第四个色阶。取值与 H5 参照的 antd-mobile 语义色一致（success / warning / danger）。
 * Int 是唯一来源：桌面组件的 RemoteViews 只能吃 Int，Compose 的 Color 从它派生。
 */
const val UrgencySafeInt = 0xFF00B578.toInt()
const val UrgencyDueSoonInt = 0xFFFF8F1F.toInt()
const val UrgencyAtRiskInt = 0xFFFF3141.toInt()

val UrgencySafe = Color(UrgencySafeInt)
val UrgencyDueSoon = Color(UrgencyDueSoonInt)
val UrgencyAtRisk = Color(UrgencyAtRiskInt)

/** 管理模式（首页）的功能色，现场模式不用它——现场只有黑白 + 三态。 */
const val AccentInt = 0xFF1677FF.toInt()
val Accent = Color(AccentInt)

/*
 * ── 管理模式的表面阶 ──────────────────────────────────────────────
 * 色相族取自 .design-qa/DESIGN.md（教务 ui/theme/Color.kt 的源码常量，经 MuMu 实机截图交叉验证）。
 * 那份契约文件的描述写明它是「给第二个 Compose 项目（取件码 App）复用同一套材质语言」用的。
 *
 * 但**档位分配不照抄**：教务的卡面取 surface-alt #F9FAFD，是因为它浮在浅色四角渐变环境层上，
 * 分离靠玻璃折射 + 描边；亮码没有环境层，页面底就是纯白，#F9FAFD 对白底的亮度差只有 0.044，
 * 比被它替换掉的 #F1F1F1（0.120）还浅。所以这里取同一族里更深两档，实测：
 *   SurfaceCard     #F2F5FA  vs 白 Δ亮度 0.089
 *   SurfaceSunken   #E8ECF3  vs 白 Δ亮度 0.164（旧 SurfaceInset 0x09000000 只有 0.035）
 * 「R ≤ G ≤ B」由 NeutralRampTest 守住，见 Theme.kt 里那段紫偏色的教训。
 */
val Canvas = OnsiteSurface
val SurfaceCard = Color(0xFFF2F5FA)
val SurfaceSunken = Color(0xFFE8ECF3)
val SurfaceDeep = Color(0xFFD5DCE7)

/** 描边。旧值是半透明 7% 黑，画在白底上合成成 #EDEDED——现在给它一个不透明的族内值。 */
val Hairline = Color(0xFFE4E8F0)
val HairlineStrong = Color(0xFFD5DCE7)

/** 需要保留语义色的描边（未识别收容行）。以前是 UrgencyDueSoon.copy(alpha = 0.4f) 现调。 */
val BorderDueSoon = Color(0xFFFFD2A5)

/*
 * ── 现场模式专用，管理模式不得借用 ──────────────────────────────
 * 现场那一屏的契约是「只有黑白 + 三态」，它要的是压在纯白上的极淡黑遮罩，
 * 而不是上面那族冷调面（冷调面在纯白上会显出蓝灰，现场不需要那种「材质」）。
 * 保留而不迁移，是「现场模式一个像素都不动」这条约束的一部分。
 */
val SurfaceInset = Color(0x09000000)
val SurfaceInsetStrong = Color(0x0F000000)

/*
 * ── 文字墨阶，替代「黑 + 七种手调 alpha」──────────────────────────
 * 改之前首页的次级文字是 OnsiteInk.copy(alpha = 0.4/0.45/0.5/0.55/0.6/0.7/0.75)，
 * 18 个不同透明度取值、77 处调用，没有一档是声明出来的。其中 0.45 那档合成成
 * 约 #8C8C8C = 3.5:1，低于 WCAG AA 的 4.5:1——柜号和「+N」都是真信息，不该用 decoration 级。
 *
 * 现在四档，对比度实测（对白底）：
 *   Ink          #101828  17.75:1   标题与正文
 *   InkSecondary #344054  10.46:1   强调次级
 *   InkMuted     #667085   4.97:1   次级信息（AA 过）
 *   InkFaint     #98A2B3   2.58:1   **只给不承载信息的字形**：关闭用的 ✕、示例水印
 */
val Ink = Color(0xFF101828)
val InkSecondary = Color(0xFF344054)
val InkMuted = Color(0xFF667085)
val InkFaint = Color(0xFF98A2B3)

/**
 * 示例数据的水印墨。这是全 App 唯一保留 alpha 的地方——
 * 它是压在卡面上的**遮罩**，不是一个可选档的文字色，所以不该被墨阶收编。
 *
 * 0.11 而不是沿用旧的 0.08：卡片现在有 SurfaceCard 不透明底，整卡又被
 * SamplePointCard 的 alpha(0.5) 压淡，水印在实机上比改之前更看不见——
 * 而「必须一眼看出不是真数据」是这条卡片的唯一职责（2026-10-13 实机截图发现）。
 */
val InkWatermark = Ink.copy(alpha = 0.11f)

/*
 * ── 语义色的同族浅底 ────────────────────────────────────────────
 * 以前状态底色是 UrgencyAtRisk.copy(alpha = 0.10f) 这类现调透明度。半透明底在
 * 卡片滚过去时会和下面的卡面混色，所以这里全部给合成到纯白之后的不透明值
 *（和 SurfaceFloating 当年改为不透明同一个理由）。
 * 取 a = 0.10，即 O = 0.10·色 + 0.90·白。
 */
val UrgencySafeTint = Color(0xFFE6F8F2)
val UrgencyDueSoonTint = Color(0xFFFFF4E9)
val UrgencyAtRiskTint = Color(0xFFFFEAEC)
val AccentTint = Color(0xFFE8F1FF)

/**
 * 浮层底色。首页是纯白，胶囊若也用纯白，与页面的亮度差是 0，只剩阴影撑分离——
 * 而阴影在强光下最先失效。不透明而不是半透明：卡片会从浮层下面滚过。
 */
val SurfaceFloating = Color(0xFFF1F1F1)
