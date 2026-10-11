package dev.showcode.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 现场模式的唯一使用环境是户外强光，对比度必须拉满：纯黑 on 纯白 ≈ 21:1。
 * 刻意不用 Material 的 onSurface（那通常是 #1C1B1F 之类，直射阳光下会发灰）。
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

val SurfaceInset = Color(0x09000000)
val SurfaceInsetStrong = Color(0x0F000000)
val Hairline = Color(0x12000000)

/**
 * 浮层底色。首页是纯白（OnsiteSurface），胶囊若也用纯白，与页面的亮度差是 0，
 * 只剩阴影撑分离——而阴影在强光下最先失效。取值等于 SurfaceInsetStrong 合成到白上的结果。
 * 刻意用不透明值而不是复用半透明的 SurfaceInsetStrong：卡片会从浮层下面滚过，
 * 半透明会让取件码从胶囊里透出来。
 */
val SurfaceFloating = Color(0xFFF1F1F1)
