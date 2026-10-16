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
 *
 * ⚠️ 2026-10-16 对齐教务时**刻意没动这三个值**：它们是 frame.json 的三态契约、是 widget
 * RemoteViews 唯一能吃到的 Int 源、现场模式也在用；而它们比教务的 Semantic 明显更鲜（教务是
 * 给一个安静的信息 App 的 muted 档）。取件码要的是「临期从三米外就得喊出来」——同族不同音量。
 */
const val UrgencySafeInt = 0xFF00B578.toInt()
const val UrgencyDueSoonInt = 0xFFFF8F1F.toInt()
const val UrgencyAtRiskInt = 0xFFFF3141.toInt()

val UrgencySafe = Color(UrgencySafeInt)
val UrgencyDueSoon = Color(UrgencyDueSoonInt)
val UrgencyAtRisk = Color(UrgencyAtRiskInt)

/**
 * 品牌蓝。取自教务 `ui/theme/Color.kt` 的 `BrandPrimary`（就是 .design-qa/DESIGN.md 的 `colors.primary`）。
 * 同族两档，分工按「这处蓝色承不承载文字」，不按明暗偏好：
 *   Accent    #0A84FF  填充、容器、选中底 —— 对白 3.38:1，只做面，不做字
 *   AccentInk #0057D9  一切线条与字：文字色、图标 tint、描边，以及承载白字的实心块
 *
 * ⚠️ AccentInk 就是教务 `Color.kt` 里的 `NavSelectedAccentLight`（那一行的注释写着
 * 「与全局主色解耦：…**#0A84FF 在灰底上对比不足，底栏单独下沉一档蓝**」）。我上一轮不知道它有，
 * 自己发明了个 `#005CBE`——两个值差 2 个数，理由几乎一字不差。2026-10-16 读到源码后换成它的原值。
 *
 * 它能在取件码这套环境层上过 AA（对最暗角 4.57:1），靠的是环境层那边的暗角 alpha 从 0.12 收到
 * 0.09（见 Environment.kt）——不是这个值本身宽松。
 *
 * 换蓝之前 Accent 只有 #1677FF 一档，对白 4.10:1，而二十多处「Accent 当 13sp 粗体文字」全靠它，
 * 一直是擦边破 AA 的一笔账；widget 那颗 11sp 的 CTA 也是同一个数。借这次对齐一次结掉。
 *
 * ⚠️ 现场模式也消费这两档（demo 退出口 / 平铺入口 / 那一颗箭头图标）。它们从 #1677FF 变成
 * #0057D9 是这一轮里现场唯一变了的像素：对白 4.10:1 → 6.24:1，21:1 那条码的契约没被碰。
 */
const val AccentInt = 0xFF0A84FF.toInt()
val Accent = Color(AccentInt)
const val AccentInkInt = 0xFF0057D9.toInt()
val AccentInk = Color(AccentInkInt)

/*
 * ── 管理模式的表面阶 ──────────────────────────────────────────────
 * 色相族取自 .design-qa/DESIGN.md（教务 ui/theme/Color.kt 的源码常量，经 MuMu 实机截图交叉验证）。
 * 那份契约文件的描述写明它是「给第二个 Compose 项目（取件码 App）复用同一套材质语言」用的。
 *
 * ⚠️ 2026-10-16 这里**推翻过自己一次**，翻的是档位不是色相：上一版写着「教务的卡面 #F9FAFD
 * 对白底只有 0.044，太浅，所以取更深两档」。那条推理的前提是「页面底是纯白」——环境层
 * （Environment.kt）铺上来之后页面不再白，卡面必须比页面**亮**才看得出是一块面，
 * 于是 #F9FAFD 反而成了正确的值：对渐变最亮那一角 ΔL 0.082，是旧值 #F2F5FA 的 0.037 的两倍。
 * 前提变了就要回头改判据，别把旧结论当事实用。
 *
 *   SurfaceCard     #F9FAFD  对渐变最亮角 ΔL 0.082 / 对最暗角 0.203
 *   SurfaceSunken   #E8ECF3  卡里的内嵌块，对卡面 ΔL 0.118
 *   SurfaceDeep     #D5DCE7  凹槽
 * 「R ≤ G ≤ B」由 NeutralRampTest 守住，见 Theme.kt 里那段紫偏色的教训。
 */
val Canvas = OnsiteSurface
val SurfaceCard = Color(0xFFF9FAFD)
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
 * 现在四档，对比度实测（对白底 / **对环境层最暗角**——环境层铺上来之后，后者才是真基准）：
 *   Ink          #101828  17.75:1 / 13.57:1   标题与正文
 *   InkSecondary #344054  10.46:1 /  8.00:1   强调次级
 *   InkMuted     #59616F   6.24:1 /  4.77:1   次级信息（两个基准都过 AA）
 *   InkFaint     #98A2B3   2.58:1 /  1.97:1   **只给不承载信息的字形**：关闭用的 ✕、示例水印
 *
 * ⚠️ InkMuted 原来是 #667085（对白 4.97:1）：那点余量在纯白上够用，叠到环境层最暗角上只剩
 * 3.80:1，而它承载的是柜号这种真信息。跟着环境层一起深一档，取两个基准都过 AA 的**最浅**值。
 */
val Ink = Color(0xFF101828)
val InkSecondary = Color(0xFF344054)
val InkMuted = Color(0xFF59616F)
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
/** Accent 的容器色。必须是 `tintOf(Accent, 0.10)` 的结果，NeutralRampTest 有断言钉着。 */
val AccentTint = Color(0xFFE6F3FF)

/**
 * 浮层底色。首页是纯白，胶囊若也用纯白，与页面的亮度差是 0，只剩阴影撑分离——
 * 而阴影在强光下最先失效。不透明而不是半透明：卡片会从浮层下面滚过。
 */
val SurfaceFloating = Color(0xFFF1F1F1)

/**
 * 玻璃材质色板（来自教务 Color.kt 的液态玻璃段）。
 * 即使不透明档也要有玻璃质感——教务的不透明回退也有渐变深度和双色描边，
 * 这是「即使没有折射也要好看」的底线。
 */
/** 液态玻璃表面高光（半透明白色叠层）。 */
val GlassHighlight = Color(0xAAFFFFFF)
/** 液态折射描边-明面（顶部与左侧边框渐变色）。 */
val GlassBorderLight = Color(0xCCFFFFFF)
/** 液态折射描边-暗面（底部与右侧边框渐变色）。 */
val GlassBorderDark = Color(0x33A6B4C9)
/** 玻璃覆盖层（卡片内微白覆盖层，增加磨砂质感）。 */
val GlassOverlay = Color(0x18FFFFFF)
