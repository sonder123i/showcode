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
 * 缓动曲线只对底栏动效族搬了（那几条是弹簧不是 tween）；换屏的 `RouteChange` 等仍用 tween
 * 默认缓动——教务的页面切换与胶囊共用一根弹簧（`NavigationMotionState`），那一层不在本轮范围。
 */
object MotionTokens {

    /** 换屏（底栏三格之间、以及进出现场模式）。 */
    val RouteChange: FiniteAnimationSpec<Float> = tween(durationMillis = 200)

    /** 无玻璃回退档（教务 `FallbackNavigationBar`）的选中度渐变：tween(220)。 */
    val FallbackWeight: FiniteAnimationSpec<Float> = tween(durationMillis = 220)

    /* ── 底栏动效核心（教务 `MotionSpring`）──────────────────────────── */

    /** 指示胶囊落位：`navSettle(0.60, 310)`——长行程要明显的果冻过冲。 */
    val NavSettle: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.60f, stiffness = 310f)

    /** 按压缩放回弹：`navRelease(0.55, 290)`。 */
    val NavRelease: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 290f)

    /** 轨道/面板漂移回位：`liquidJellyRebound(0.66, 320)`。 */
    val LiquidJellyRebound: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.66f, stiffness = 320f)

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