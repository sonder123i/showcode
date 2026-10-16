package dev.showcode.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 动效 token。时长与弹簧取自教务 `ui/theme/MotionTokens.kt`（.design-qa/DESIGN.md 的 `motion` 段），
 * 这是「看起来像一家人」之外「摸起来像一家人」的那一维——L0 收口时量过，那时本仓库 grep 到
 * **0 处** animate/spring/tween，是全 App 唯一完全没有动效的系统。
 *
 * 教务那套的完整时长表是 100 / 180(tab) / 180(nav-label) / 200 / 250 / 260(nav-indicator) /
 * 400 / 400(dialog) / 1400(emphasis) ms，外加几个具名弹簧。这里**只声明真的有人消费的那几条**：
 * 留一个没人消费的 token，和「M3 给了 5 个容器 role、代码消费 0 个」是同一种毛病。
 *
 * 缓动曲线没有跟着搬：DESIGN.md 只记了时长与弹簧的阻尼/刚度，没记 `CubicBezierEasing` 的控制点，
 * 而曲线是能看出差别的，不该猜。所以这里用 tween 的默认缓动，等读得到教务源码再对齐。
 */
object MotionTokens {

    /** 换屏（底栏三格之间、以及进出现场模式）。 */
    val RouteChange: FiniteAnimationSpec<Float> = tween(durationMillis = 200)

    /** 底栏那颗选中指示的颜色切换：教务的 nav-indicator 260ms。 */
    val NavIndicator: FiniteAnimationSpec<Color> = tween(durationMillis = 260)

    /** 同一个时长，动画的是 Float（标签淡出这类）。 */
    val NavIndicatorFloat: FiniteAnimationSpec<Float> = tween(durationMillis = 260)

    /** 无玻璃回退档（教务 `FallbackNavigationBar`）的选中度渐变：tween(220)。 */
    val FallbackWeight: FiniteAnimationSpec<Float> = tween(durationMillis = 220)

    /**
     * 指示胶囊**滑行**到新格子，以及图标随选中放大/上浮。
     *
     * 取自教务 `theme/NavigationMotion.kt` 的 `MotionProfile`——那就是它导航那一套的档案
     * （`MotionProfile.Navigation = PageTransitionSpec()` 也在里面），其中
     * `iconSpring() = spring(0.68f, 420f)`、`IconMillis = 280`。指示器的滑动借同一个弹簧。
     */
    val IconSelection: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.68f, stiffness = 420f)

    /** 同一个弹簧，动画的是 Dp：指示胶囊滑到下一格的位移。 */
    val IndicatorTravel: FiniteAnimationSpec<Dp> = spring(dampingRatio = 0.68f, stiffness = 420f)

    /** 按下反馈：教务 `MotionProfile.PressMillis = 150`。按下是**下沉 + 微缩**，不是整键缩放。 */
    val PressFeedback: FiniteAnimationSpec<Float> = tween(durationMillis = 150)

    /**
     * 换屏的位移、缩放与错峰，逐条取自教务 `MotionProfile`：
     * `PageTransitionSpec(enterDistance 8, exitDistance 12, exitScale 0.985)`、
     * `DetailStaggerMillis = 35`。
     *
     * 位移**只有 8/12dp**——小得要留意才看得出，这正是它显得"稳"而不是"在动"的原因。
     * 上一版只做了淡化，没有位移也没有缩放。
     */
    val RouteEnterDistance: Dp = 8.dp
    val RouteExitDistance: Dp = 12.dp
    const val RouteExitScale = 0.985f

    /** 出场比入场短一档，换屏才不像两边各自慢慢挪。 */
    val RouteExit: FiniteAnimationSpec<Float> = tween(durationMillis = 160)

    /** 模块错峰：教务 `MotionProfile.DetailStaggerMillis`。 */
    const val DetailStaggerMillis = 35L
}