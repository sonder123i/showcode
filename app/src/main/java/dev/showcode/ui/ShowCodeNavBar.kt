package dev.showcode.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
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

/*
 * 底部导航：待取件 / 身份码 / 设置。
 *
 * 几何、材质、交互逐条对着教务 `CapsuleNavigationBar` 的 `NavTab` / `NavBarMetrics` /
 * `FallbackNavigationBar` 做（教务仓库只读，值从源码抄，不猜）：
 *
 * | 项 | 值 | 出处 |
 * |---|---|---|
 * | 轨道 | 满宽（横 16/10、纵 8/6 留白），高 72/62（有折射外观）或 64/58 | `NavBarMetrics` |
 * | 键 | 等宽 `weight(1f)`，图标 24dp | `NavTab` |
 * | 指示胶囊 | 一格宽 × 56/48 高，内衬 4/6 | `indicatorHeight` / `barPadding` |
 * | 胶囊表面 | 有折射白 0.34→按下 0.14；无折射 Black×0.1 按下淡出 | `NavSelectedSolid*` |
 * | 轨道表面 | 有折射 White@0.28 + 高光 0.16/投影 0.08；无折射 FAFAFA@0.15 无高光投影 | `containerColor` |
 * | 无玻璃 | 整条换 60dp 实心 Surface、无滑行胶囊、收起不生效 | `FallbackNavigationBar` |
 * | 选中度 | 连续 0→1（不是布尔）：图标放大 10%/上浮 2dp、字重 0.55 跳变 | `NavTab` |
 *
 * 批注记：动效核心（1.39 按压胀、速度拉伸、拖拽、单胶囊收起）在后续批次。
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

/** 收起态（滚动往下读时）的轨道高。批 5 换单胶囊形态后这个常量退休。 */
private val NAV_BAR_HEIGHT_MINIMIZED = 46.dp

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
    /** 滚动往下读时收到的请求。收起后只剩图标，标签被裁出视野。 */
    minimized: Boolean = false,
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

    val onTab: (NavBarTab) -> Unit = {
        when (it) {
            NavBarTab.HOME -> onHome()
            NavBarTab.IDENTITY -> onIdentity()
            NavBarTab.SETTINGS -> onSettings()
        }
    }

    /* 整条块：几何三件套（轨道高 / 纵留白 / 横留白）都来自教务 `NavBarMetrics`，
       底部安全区走 navigationBarsPadding——这是底栏，状态栏的上留白不该混进来。 */
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
                onTab = onTab,
            )
            return@Box
        }

        val fullHeight = trackHeight
        val barHeight by animateDpAsState(
            targetValue = if (minimized) NAV_BAR_HEIGHT_MINIMIZED else fullHeight,
            animationSpec = MotionTokens.IndicatorTravel,
            label = "nav-bar-height",
        )
        val labelAlpha by animateFloatAsState(
            targetValue = if (minimized) 0f else 1f,
            animationSpec = MotionTokens.NavIndicatorFloat,
            label = "nav-label-alpha",
        )
        val selectedIndex = NAV_TABS.indexOf(current).coerceAtLeast(0)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val barPaddingPx = with(density) { barPadding.toPx() }
            val tabWidth = (constraints.maxWidth.toFloat() - barPaddingPx * 2f) / NAV_TABS.size
            val tabWidthDp = with(density) { tabWidth.toDp() }

            /* 指示胶囊滑到选中那一格。位移本身就是反馈：手指落在第三格，
               眼睛跟着那枚胶囊滑过去。 */
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidthDp * selectedIndex,
                animationSpec = MotionTokens.IndicatorTravel,
                label = "nav-indicator-offset",
            )

            /* 轨道。玻璃档不叠 Compose 的投影（那画的是一团不透明黑）；
               高光/投影交给库自己的 Highlight/Shadow——有折射外观才有，无折射档是 null
               （教务 cba2a09：「轨道无投影」）。
               blur 收**像素**（`GlassRecipe.blurDp` 注释里钉了字节码证据），必须 `.toPx()`。 */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
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
            ) {
                /* 选中胶囊。表面值是教务底栏**实际消费**的那组（不是 0.94——那个常量在教务
                   零消费，出处修正见 GlassMaterials）。批 3 接上按压淡出（0.34→0.14 /
                   Black×0.1 淡出）。 */
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorOffset)
                        .width(tabWidthDp)
                        .height(indicatorHeight.coerceAtMost(barHeight - barPadding * 2))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .graphicsLayer {
                            alpha = if (lensLook) {
                                GlassMaterials.NavSelectedSolidAlpha
                            } else {
                                GlassMaterials.NavLegacyIndicatorAlpha
                            }
                        }
                        .background(if (lensLook) Color.White else Color.Black),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavBarKey(
                        label = stringResource(R.string.home_title),
                        weight = navKeyWeight(current == NavBarTab.HOME, MotionTokens.IconSelection),
                        labelAlpha = labelAlpha,
                        onClick = { onTab(NavBarTab.HOME) },
                        iconRes = R.drawable.ic_nav_pickup,
                    )
                    NavBarKey(
                        label = stringResource(R.string.identity_title),
                        weight = navKeyWeight(current == NavBarTab.IDENTITY, MotionTokens.IconSelection),
                        labelAlpha = labelAlpha,
                        onClick = { onTab(NavBarTab.IDENTITY) },
                        iconRes = R.drawable.ic_nav_identity,
                    )
                    NavBarKey(
                        label = stringResource(R.string.settings_title),
                        weight = navKeyWeight(current == NavBarTab.SETTINGS, MotionTokens.IconSelection),
                        labelAlpha = labelAlpha,
                        onClick = { onTab(NavBarTab.SETTINGS) },
                        iconRes = R.drawable.ic_nav_settings,
                    )
                }
            }
        }
    }
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
 * `weight` 是一条**连续**的选中度（0→1），不是布尔：图标按它放大、上浮，墨色按它插值。
 * 未选中那一对墨是照教务取的：图标浅一档、标签深一档。选中的两支**分色**：
 * 图标走 accent、标签走 onSurface（教务 `NavTab` 原文如此——上一版标签也染成 accent，
 * 那是只看了隐藏 tint 层的 `forceAccent` 分支）。本仓库的令牌映射：
 * 图标 `InkMuted`、标签 `InkSecondary`、选中图标 `AccentInk`、选中标签 `Ink`。
 *
 * 字重随选中在 0.55 这条线上跳变（教务原文）。三格等宽固定，加粗只让标签向两边各长一点，
 * 整排不动。
 *
 * `indication = null`：家族把反馈交给动效，不叠水波纹。语义仍走 `Role.Tab`，读屏不受影响。
 */
@Composable
private fun RowScope.NavBarKey(
    label: String,
    weight: Float,
    labelAlpha: Float,
    onClick: () -> Unit,
    iconRes: Int,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MotionTokens.PressFeedback,
        label = "nav-key-press",
    )
    val tint by animateColorAsState(
        targetValue = if (weight > 0.5f) AccentInk else InkMuted,
        animationSpec = MotionTokens.NavIndicator,
        label = "nav-key-tint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (weight > 0.5f) Ink else InkSecondary,
        animationSpec = MotionTokens.NavIndicator,
        label = "nav-key-label",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = weight > 0.5f,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(NAV_ICON_SIZE)
                .graphicsLayer {
                    val scale = (1f + 0.10f * weight) * (1f - 0.04f * pressProgress)
                    scaleX = scale
                    scaleY = scale
                    translationY = -2.dp.toPx() * weight + 1.dp.toPx() * pressProgress
                },
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = ShowCodeText.Micro.copy(
                fontWeight = if (weight > 0.55f) FontWeight.Bold else FontWeight.Normal,
            ),
            color = labelColor.copy(alpha = labelColor.alpha * labelAlpha),
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
    onTab: (NavBarTab) -> Unit,
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
            NavBarKey(
                label = stringResource(R.string.home_title),
                weight = navKeyWeight(current == NavBarTab.HOME, MotionTokens.FallbackWeight),
                labelAlpha = 1f,
                onClick = { onTab(NavBarTab.HOME) },
                iconRes = R.drawable.ic_nav_pickup,
            )
            NavBarKey(
                label = stringResource(R.string.identity_title),
                weight = navKeyWeight(current == NavBarTab.IDENTITY, MotionTokens.FallbackWeight),
                labelAlpha = 1f,
                onClick = { onTab(NavBarTab.IDENTITY) },
                iconRes = R.drawable.ic_nav_identity,
            )
            NavBarKey(
                label = stringResource(R.string.settings_title),
                weight = navKeyWeight(current == NavBarTab.SETTINGS, MotionTokens.FallbackWeight),
                labelAlpha = 1f,
                onClick = { onTab(NavBarTab.SETTINGS) },
                iconRes = R.drawable.ic_nav_settings,
            )
        }
    }
}
