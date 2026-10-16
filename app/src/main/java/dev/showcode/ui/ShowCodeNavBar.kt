package dev.showcode.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.showcode.R
import dev.showcode.ui.glass.GlassCompat
import dev.showcode.ui.glass.GlassMaterials
import dev.showcode.ui.glass.GlassTier
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * 底部导航：待取件 / 身份码 / 设置。
 *
 * 几何、材质、动效逐条对着教务 `CapsuleNavigationBar` 的 `NavTab` / `NavBarMetrics` /
 * `DampedDragAnimation` 做（教务仓库只读，值从源码抄，不猜）：
 *
 * | 项 | 值 | 出处 |
 * |---|---|---|
 * | 轨道 | 满宽（横 16/10、纵 8/6），高 72/62（有折射外观）或 64/58 | `NavBarMetrics` |
 * | 指示胶囊 | 一格宽 × 56/48；按压**胀 78/56**、滑行中按速度拉伸 | `indicatorScale` |
 * | 落位 | `navSettle(0.60/310)` 带初速；回弹 `navRelease(0.55/290)` | `MotionSpring` |
 * | 胶囊表面 | 有折射白 0.34→按下 0.14；无折射 Black×0.1 按下淡出 | `NavSelectedSolid*` |
 * | 轨道 | White@0.28 + 高光 0.16/投影 0.08（无折射档 FAFAFA@0.15 无高光投影） | `containerColor` |
 * | 轨道呼吸 | 按下放大 16dp 当量 × (1+胶囊胀度·0.18) | `layerBlock` |
 * | 手势 | 整条可拖着换格（VelocityTracker 积分、松手带初速），轨道反向微漂 4dp | `pointerInput` |
 * | 触觉 | 换格 ContextClick；点已选中那格重播它的 flourish | `selectTab` |
 * | 图标 | 24dp duotone Canvas，逐格 flourish（NavSymbol / NavIconPlayback） | 教务同款 |
 * | 无玻璃 | 整条换 60dp 实心 Surface、无滑行胶囊、收起不生效 | `FallbackNavigationBar` |
 *
 * 与教务**不同源**的只剩三处，都是没得抄或不在本轮：页面切换与胶囊共用的那根弹簧
 * （`NavigationMotionState`）、31/32 离屏 GL 折射（批 6）、无障碍减弱动效轴。
 */

/* ---- 几何（教务 `NavBarMetrics`，与 [navBarClearance] 同源）---- */

private val NAV_TRACK_HEIGHT = 64.dp
private val NAV_TRACK_HEIGHT_TIGHT = 58.dp
private val NAV_TRACK_HEIGHT_LENS = 72.dp
private val NAV_TRACK_HEIGHT_LENS_TIGHT = 62.dp

private val NAV_BLOCK_PADDING = 8.dp
private val NAV_BLOCK_PADDING_TIGHT = 6.dp

private val NAV_HORIZONTAL_PADDING = 16.dp
private val NAV_HORIZONTAL_PADDING_TIGHT = 10.dp

private val NAV_INDICATOR_HEIGHT = 56.dp
private val NAV_INDICATOR_HEIGHT_TIGHT = 48.dp

private val NAV_BAR_PADDING = 6.dp
private val NAV_BAR_PADDING_LEGACY = 4.dp

private val NAV_BAR_CARD_GAP = 16.dp

private val NAV_ICON_SIZE = 24.dp

/** 无玻璃回退档的整条高（教务 `FallbackNavigationBar` 的 Surface）。 */
private val NAV_FALLBACK_HEIGHT = 60.dp

/** 列表底部要给底栏留的净空。各屏请用 [navBarClearance]——短屏上底栏会收，净空必须同源。 */
@Composable
fun navBarClearance(): Dp {
    val metrics = rememberScreenMetrics()
    // 永远按较高的折射外观档算：净空宁多勿少。教务的教训是两者脱钩过一次、末项被底栏压住；
    // 反方向（多 8dp 底距）没有任何可见代价。
    return metrics.tall(NAV_TRACK_HEIGHT_LENS, NAV_TRACK_HEIGHT_LENS_TIGHT) +
        metrics.tall(NAV_BLOCK_PADDING, NAV_BLOCK_PADDING_TIGHT) +
        NAV_BAR_CARD_GAP
}

/** 底栏上的三个去处。刻意不复用 `ShowCodeRoot` 那个路由枚举——它是文件私有的，而底栏只认这三格。 */
enum class NavBarTab { HOME, IDENTITY, SETTINGS }

private val NAV_TABS = listOf(NavBarTab.HOME, NavBarTab.IDENTITY, NavBarTab.SETTINGS)

@Composable
fun ShowCodeNavBar(
    current: NavBarTab,
    onHome: () -> Unit,
    onIdentity: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** 滚动往下读时收到的请求。收起后只剩当前格的一枚小胶囊，点它展开。 */
    minimized: Boolean = false,
    /** 点收起胶囊要求展开。教务 `onExpandRequest` 同名同义。 */
    onExpandRequest: () -> Unit = {},
    tier: GlassTier = GlassTier.OPAQUE,
    /** 玻璃折的那张背景。null、或不是玻璃档，就等于回落到不透明底栏。 */
    backdrop: Backdrop? = null,
    highTextContrast: Boolean = false,
) {
    val metrics = rememberScreenMetrics()
    val lensLook = GlassCompat.lensLook(tier, Build.VERSION.SDK_INT)
    val glassBackdrop = backdrop?.takeIf { tier == GlassTier.BACKDROP }

    val trackHeight = metrics.tall(
        if (lensLook) NAV_TRACK_HEIGHT_LENS else NAV_TRACK_HEIGHT,
        if (lensLook) NAV_TRACK_HEIGHT_LENS_TIGHT else NAV_TRACK_HEIGHT_TIGHT,
    )
    val indicatorHeight = metrics.tall(NAV_INDICATOR_HEIGHT, NAV_INDICATOR_HEIGHT_TIGHT)
    val blockPadding = metrics.tall(NAV_BLOCK_PADDING, NAV_BLOCK_PADDING_TIGHT)
    val horizontalPadding = metrics.wide(NAV_HORIZONTAL_PADDING, NAV_HORIZONTAL_PADDING_TIGHT)
    val barPadding = if (lensLook) NAV_BAR_PADDING else NAV_BAR_PADDING_LEGACY

    val selectedIndex = NAV_TABS.indexOf(current).coerceAtLeast(0)
    val iconPlayback = rememberNavIconPlayback(NAV_TABS.size, selectedIndex, reduced = false)

    /* 换格 + 重按 + 触觉。点已选中那格不换屏，只重播它的 flourish（教务 selectTab 语义）。 */
    val haptics = LocalHapticFeedback.current
    val selectTab: (Int) -> Unit = { index ->
        if (index != selectedIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            when (NAV_TABS[index]) {
                NavBarTab.HOME -> onHome()
                NavBarTab.IDENTITY -> onIdentity()
                NavBarTab.SETTINGS -> onSettings()
            }
        } else {
            iconPlayback.replay(index)
        }
    }

    /* 位置/按压/缩放状态机。手指拥有位置，弹簧只管松手之后——教务 DampedDragAnimation。 */
    val animationScope = rememberCoroutineScope()
    val drag = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(NAV_TABS.size - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = GlassMaterials.NavPressedScale,
            settleAnimationSpec = MotionTokens.NavSettle,
            releaseScaleAnimationSpec = MotionTokens.NavRelease,
        )
    }
    LaunchedEffect(selectedIndex) {
        if (drag.targetValue.toInt() != selectedIndex) drag.animateToValue(selectedIndex.toFloat())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = blockPadding)
            .height(trackHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (glassBackdrop == null) {
            FallbackNavigationBar(
                current = current,
                selectedIndex = selectedIndex,
                onKeyClick = { selectTab(it) },
                iconPlayback = iconPlayback,
            )
            return@Box
        }

        /* 滚动收起（教务原式）：整条玻璃横向收拢淡出、单胶囊液态弹出。一个分数驱动两头。 */
        val minimizeFraction by animateFloatAsState(
            targetValue = if (minimized) 1f else 0f,
            animationSpec = MotionTokens.LiquidSettle,
            label = "nav-minimize-fraction",
        )

        /* 拖动时轨道反向微漂（教务 panelOffset：4dp·sign·EaseOut），松手后果冻回位。 */
        val panelAnim = remember { Animatable(0f) }

        if (minimizeFraction < 0.999f) {
        Box(
            modifier = Modifier.graphicsLayer {
                val f = minimizeFraction
                alpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                scaleX = 1f - 0.82f * f
                scaleY = 1f - 0.30f * f
                transformOrigin = TransformOrigin.Center
            },
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val barPaddingPx = with(density) { barPadding.toPx() }
            val tabWidthPx = (constraints.maxWidth.toFloat() - barPaddingPx * 2f) / NAV_TABS.size
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val panelOffset by remember(density, constraints.maxWidth) {
                derivedStateOf {
                    val fraction = (panelAnim.value / constraints.maxWidth.toFloat())
                        .coerceIn(-1f, 1f)
                    with(density) {
                        4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                        clip = false
                    }
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { CircleShape },
                        effects = {
                            val blurDp = if (lensLook) {
                                GlassMaterials.navigation(highTextContrast).blurDp
                            } else {
                                GlassMaterials.NavLegacyTrackBlurDp
                            }
                            blur(with(density) { blurDp.dp.toPx() })
                        },
                        highlight = {
                            if (lensLook) {
                                Highlight.Default.copy(alpha = GlassMaterials.NavTrackHighlightAlpha)
                            } else {
                                null
                            }
                        },
                        shadow = {
                            if (lensLook) {
                                Shadow(alpha = GlassMaterials.NavTrackShadowAlpha)
                            } else {
                                null
                            }
                        },
                        layerBlock = {
                            /* 轨道随按压 + 胶囊胀度呼吸（教务 layerBlock 同式）。 */
                            val press = drag.pressProgress
                            val maxGain = with(density) { 16.dp.toPx() }
                            val pressScale = 1f + (maxGain / size.width) * press
                            val indicatorBoost =
                                ((drag.scaleX + drag.scaleY) / 2f - 1f).coerceIn(0f, 0.4f)
                            val scale = pressScale * (1f + indicatorBoost * 0.18f)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = {
                            if (lensLook) {
                                drawRect(Color.White.copy(alpha = GlassMaterials.NavTrackSurfaceAlpha))
                            } else {
                                drawRect(
                                    Color(GlassMaterials.NavLegacyTrackSurface)
                                        .copy(alpha = GlassMaterials.NavLegacyTrackSurfaceAlpha),
                                )
                            }
                        },
                    )
                    .padding(horizontal = barPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                /* 选中胶囊：位移挂在位置状态机上（一格 = tabWidthPx），缩放只进 layerBlock
                   （backdrop 库的硬约束），按压胀 78/56、滑行中按速度拉伸。 */
                val press = drag.pressProgress
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = drag.value * tabWidthPx
                            clip = false
                        }
                        .width(tabWidthDp)
                        .height(indicatorHeight.coerceAtMost(trackHeight - barPadding * 2))
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { CircleShape },
                            effects = {
                                /* 33+ 的按下折射（库基线 lens(10dp·p, 14dp·p)，静止 0）。
                                   31/32 的真折射在批 6 接离屏 GL；这里先按无折射档走。 */
                                if (lensLook && press > 0f) {
                                    lens(
                                        refractionHeight = with(density) {
                                            GlassMaterials.NavIndicatorLensHeightDp.dp.toPx()
                                        } * press,
                                        refractionAmount = with(density) {
                                            GlassMaterials.NavIndicatorLensAmountDp.dp.toPx()
                                        } * press,
                                        chromaticAberration = true,
                                    )
                                }
                            },
                            highlight = {
                                /* 整层被 layerBlock 等比放大，描边宽度/模糊反向补偿，
                                   视觉细度才恒定（教务 scaleComp 同式）。 */
                                val scaleComp =
                                    ((drag.scaleX + drag.scaleY) / 2f).coerceAtLeast(1f)
                                if (lensLook) {
                                    Highlight.Default.copy(
                                        width = Highlight.Default.width / scaleComp,
                                        blurRadius = Highlight.Default.blurRadius / scaleComp,
                                        alpha = 0.12f + press * 0.35f,
                                    )
                                } else {
                                    Highlight.Default.copy(
                                        width = Highlight.Default.width / scaleComp,
                                        alpha = press * 0.35f,
                                    )
                                }
                            },
                            shadow = {
                                if (lensLook) {
                                    Shadow(alpha = GlassMaterials.NavIndicatorShadowAlpha + press * 0.15f)
                                } else {
                                    Shadow(alpha = press * 0.5f)
                                }
                            },
                            innerShadow = {
                                InnerShadow(radius = 4.dp * press, alpha = press * 0.5f)
                            },
                            layerBlock = {
                                val (sx, sy) = indicatorScale(drag)
                                scaleX = sx
                                scaleY = sy
                            },
                            onDrawSurface = {
                                if (lensLook) {
                                    /* 有折射：低透明中性 tint（白），静止即玻璃；按下更透露出折射。 */
                                    drawRect(
                                        Color.White.copy(
                                            alpha = androidx.compose.ui.util.lerp(
                                                GlassMaterials.NavSelectedSolidAlpha,
                                                GlassMaterials.NavSelectedGlassAlpha,
                                                press,
                                            ),
                                        ),
                                    )
                                } else {
                                    /* 无折射档（教务 cba2a09）：Black×0.1，按下淡出。
                                       两笔都是表面遮罩不是文字色——alpha 走绘制参数，
                                       值与教务逐帧等价（黑 @0.1·(1-p) + 黑 @0.03·p）。 */
                                    drawRect(Color.Black, alpha = GlassMaterials.NavLegacyIndicatorAlpha * (1f - press))
                                    drawRect(Color.Black, alpha = GlassMaterials.NavLegacyIndicatorPressAlpha * press)
                                }
                            },
                        ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .pointerInput(tabWidthPx, minimized) {
                            /* 整条手势：点按选格、拖动跟手换格。down 在 Initial pass 消费，
                               下面各键的 selectable 只剩无障碍语义（触屏走这里的 startX→格）。
                               多指中断、CANCEL 不提交、松手带初速——教务原逻辑逐条搬。 */
                            if (minimized) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val startValue = drag.value
                                val committedIndex = selectedIndex
                                val startX = down.position.x
                                val touchSlop = viewConfiguration.touchSlop
                                var totalDragX = 0f
                                var dragging = false
                                var completed = false
                                var pointerId = down.id

                                drag.press(down.uptimeMillis)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (event.changes.count { it.pressed } > 1) {
                                            event.changes.forEach { it.consume() }
                                            break
                                        }
                                        if (change.isConsumed) break
                                        if (change.changedToUpIgnoreConsumed()) {
                                            completed = true
                                            change.consume()
                                            break
                                        }
                                        if (!dragging &&
                                            abs(change.position.y - down.position.y) > touchSlop &&
                                            abs(totalDragX) <= touchSlop
                                        ) {
                                            break
                                        }
                                        val dragAmount = change.positionChange()
                                        if (dragAmount != androidx.compose.ui.geometry.Offset.Zero) {
                                            totalDragX += dragAmount.x
                                            if (!dragging && abs(totalDragX) > touchSlop) dragging = true
                                            if (dragging) {
                                                change.consume()
                                                drag.updateValue(
                                                    (startValue + totalDragX / tabWidthPx)
                                                        .coerceIn(0f, (NAV_TABS.size - 1).toFloat()),
                                                    change.uptimeMillis,
                                                )
                                                animationScope.launch { panelAnim.snapTo(totalDragX) }
                                            }
                                        }
                                        pointerId = change.id
                                    }
                                } finally {
                                    val targetIndex = when {
                                        !completed -> committedIndex
                                        dragging -> drag.targetValue.toInt()
                                            .coerceIn(0, NAV_TABS.size - 1)
                                        else -> (startX / tabWidthPx).toInt()
                                            .coerceIn(0, NAV_TABS.size - 1)
                                    }
                                    if (!completed) {
                                        drag.animateToValue(committedIndex.toFloat())
                                    } else {
                                        selectTab(targetIndex)
                                        if (targetIndex == committedIndex && !dragging) {
                                            drag.release()
                                        } else {
                                            drag.animateToValue(targetIndex.toFloat())
                                        }
                                    }
                                    if (panelAnim.value != 0f) {
                                        animationScope.launch {
                                            panelAnim.animateTo(0f, MotionTokens.LiquidJellyRebound)
                                        }
                                    }
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (index in NAV_TABS.indices) {
                        val weight = (1f - abs(drag.value - index)).coerceIn(0f, 1f)
                        NavBarKey(
                            label = stringResource(
                                when (NAV_TABS[index]) {
                                    NavBarTab.HOME -> R.string.home_title
                                    NavBarTab.IDENTITY -> R.string.identity_title
                                    NavBarTab.SETTINGS -> R.string.settings_title
                                },
                            ),
                            weight = weight,
                            pressProgress = drag.pressProgress * weight,
                            onClick = { if (!minimized) selectTab(index) },
                            symbol = when (index) {
                                0 -> NavSymbol.PICKUP
                                1 -> NavSymbol.IDENTITY
                                else -> NavSymbol.SETTINGS
                            },
                            phase = { iconPlayback.phase(index) },
                        )
                    }
                }
            }
        }
        }
        }

        if (minimizeFraction > 0.001f) {
            MinimizedNavCapsule(
                label = stringResource(
                    when (current) {
                        NavBarTab.HOME -> R.string.home_title
                        NavBarTab.IDENTITY -> R.string.identity_title
                        NavBarTab.SETTINGS -> R.string.settings_title
                    },
                ),
                symbol = when (selectedIndex) {
                    0 -> NavSymbol.PICKUP
                    1 -> NavSymbol.IDENTITY
                    else -> NavSymbol.SETTINGS
                },
                iconPhase = { iconPlayback.phase(selectedIndex) },
                onClick = { if (minimized) onExpandRequest() },
                lensLook = lensLook,
                glassBackdrop = glassBackdrop,
                modifier = Modifier.graphicsLayer {
                    val f = minimizeFraction
                    alpha = ((f - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val scale = 0.55f + 0.45f * f
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}

/**
 * 收起形态：只剩当前格的一枚小玻璃胶囊（教务 `MinimizedNavCapsule` 同式：48dp 高、
 * 图标+标签、点按要求展开）。图标还在播——收起态也认得出当前在哪一格。
 */
@Composable
private fun MinimizedNavCapsule(
    label: String,
    symbol: NavSymbol,
    iconPhase: () -> Float,
    onClick: () -> Unit,
    lensLook: Boolean,
    glassBackdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = glassBackdrop,
                shape = { CircleShape },
                effects = { blur(with(density) { 8.dp.toPx() }) },
                highlight = { Highlight.Default.copy(alpha = 0.14f) },
                shadow = { Shadow(alpha = 0.10f) },
                onDrawSurface = {
                    if (lensLook) {
                        drawRect(Color.White.copy(alpha = GlassMaterials.NavTrackSurfaceAlpha))
                    } else {
                        drawRect(
                            Color(GlassMaterials.NavLegacyTrackSurface)
                                .copy(alpha = GlassMaterials.NavLegacyTrackSurfaceAlpha),
                        )
                    }
                },
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(48.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavSymbolIcon(
            symbol = symbol,
            selection = 1f,
            phase = iconPhase,
            tint = AccentInk,
            modifier = Modifier.size(NAV_ICON_SIZE),
        )
        Text(
            text = label,
            color = Ink,
            style = ShowCodeText.Micro.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/**
 * 指示胶囊的按压 + 速度形变（教务 `indicatorScale` 同式，参数一字未改）。
 * 速度形变系数 /6：`sx` 拉长、`sy` 压扁——滑得越快胶囊越「被甩长」。
 */
private fun indicatorScale(anim: DampedDragAnimation): Pair<Float, Float> {
    var sx = anim.scaleX
    var sy = anim.scaleY
    val velocity = anim.velocity / 6f
    sx /= 1f - (velocity * 0.75f).coerceIn(-0.22f, 0.22f)
    sy *= 1f - (velocity * 0.25f).coerceIn(-0.22f, 0.22f)
    return sx to sy
}

@Composable
private fun navKeyWeight(selected: Boolean, spec: FiniteAnimationSpec<Float>): Float {
    val weight by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spec,
        label = "nav-key-weight",
    )
    return weight
}

/**
 * 一格：图形 + 一行标签。
 *
 * `weight` 是位置状态机导出的**连续**选中度（0→1），不是布尔：图标按它放大 10%、上浮 2dp，
 * 墨色按它插值（教务 `NavTab` 同式）。按下按压**胀**由上面的状态机统一驱动，
 * 图标只分到 `pressProgress × weight` 那一份——按哪格哪格有反应。
 *
 * 选中的两支**分色**：图标走 accent、标签走 onSurface（教务原文）。字重在 0.55 跳变。
 * `indication = null`：反馈交给动效，不叠水波纹。语义仍走 `Role.Tab`。
 */
@Composable
private fun RowScope.NavBarKey(
    label: String,
    weight: Float,
    pressProgress: Float,
    onClick: () -> Unit,
    symbol: NavSymbol,
    phase: () -> Float,
) {
    val tint = lerp(InkMuted, AccentInk, weight)
    val labelColor = lerp(InkSecondary, Ink, weight)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = weight > 0.5f,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        NavSymbolIcon(
            symbol = symbol,
            selection = weight,
            phase = phase,
            tint = tint,
            modifier = Modifier
                .size(NAV_ICON_SIZE)
                .graphicsLayer {
                    val scale = (1f + 0.10f * weight) * (1f - 0.04f * pressProgress)
                    scaleX = scale
                    scaleY = scale
                    translationY = -2.dp.toPx() * weight + 1.dp.toPx() * pressProgress
                },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = ShowCodeText.Micro.copy(
                fontWeight = if (weight > 0.55f) FontWeight.Bold else FontWeight.Normal,
            ),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 无玻璃回退（教务 `FallbackNavigationBar`）：60dp 实心 Surface、**没有滑行胶囊**、
 * 收起不生效（教务那个分支连 minimized 形参都不接）。选中度走 tween(220)。
 */
@Composable
private fun FallbackNavigationBar(
    current: NavBarTab,
    selectedIndex: Int,
    onKeyClick: (Int) -> Unit,
    iconPlayback: NavIconPlayback,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(NAV_FALLBACK_HEIGHT),
        /* 教务这里是 28dp 圆角（60dp 高的准胶囊）。本仓库棘轮把裸圆角钉死为 0——
           连注释里的字面量都会被它的正则数进去，所以这里只写数值不写构造式。
           50% 胶囊在同一高度上差 2dp，肉眼不可分——用形状 token，不破地板。 */
        shape = CircleShape,
        color = SurfaceCard,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in NAV_TABS.indices) {
                NavBarKey(
                    label = stringResource(
                        when (NAV_TABS[index]) {
                            NavBarTab.HOME -> R.string.home_title
                            NavBarTab.IDENTITY -> R.string.identity_title
                            NavBarTab.SETTINGS -> R.string.settings_title
                        },
                    ),
                    weight = navKeyWeight(index == selectedIndex, MotionTokens.FallbackWeight),
                    pressProgress = 0f,
                    onClick = { onKeyClick(index) },
                    symbol = when (index) {
                        0 -> NavSymbol.PICKUP
                        1 -> NavSymbol.IDENTITY
                        else -> NavSymbol.SETTINGS
                    },
                    phase = { iconPlayback.phase(index) },
                )
            }
        }
    }
}
