package dev.showcode.ui.glass

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.Shape

/*
 * 折射引擎的适配层。引擎本体（GlassLens* / PhysicalLensSurface / ChromaticFringe）
 * 从教务 `ui/system/glass/` 原样搬入，这里只补它对教务宿主系统的那几个依赖：
 * 材质数据（GlassMaterialSpec）、能力判据（isRuntimeLensEnabled / canUseLiquidLens）、
 * 无障碍档（GlassAccessibilityMode）。形状/平台判据逐条保留教务原文的注释——
 * 那些注释本身就是踩坑记录。
 */

@androidx.compose.runtime.Immutable
data class GlassOpticsSpec(
    val chromaticAberration: Boolean = false,
    val velocityForFullEffect: Float = 2200f,
)

@androidx.compose.runtime.Immutable
data class GlassMaterialSpec(
    val blurDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val shadowAlpha: Float,
    val depthEffect: Boolean = false,
    val optics: GlassOpticsSpec = GlassOpticsSpec(),
) {
    val chromaticAberration: Boolean
        get() = optics.chromaticAberration
}

@androidx.compose.runtime.Immutable
data class GlassAccessibilityMode(
    val reduceMotion: Boolean,
    val highContrast: Boolean,
)

/**
 * 指示胶囊的材质（教务角色表 `Interactive` 行：blur 0 / 折射 12-18 / 色散 on）。
 * 折射只在按下时长出来（`NavIndicatorRefractionFloor = 0`），静止一个像素都不折。
 */
fun interactiveLensSpec(highContrast: Boolean): GlassMaterialSpec = GlassMaterialSpec(
    blurDp = 0f,
    refractionHeightDp = GlassMaterials.NavIndicatorLensHeightDp,
    refractionAmountDp = GlassMaterials.NavIndicatorLensAmountDp,
    surfaceAlpha = if (highContrast) 0.28f else 0.08f,
    borderAlpha = if (highContrast) 0.34f else 0.16f,
    shadowAlpha = 0.12f,
    optics = GlassOpticsSpec(chromaticAberration = true),
)

/** 平台是否真支持 RuntimeShader/AGSL（Android 13+）。教务 `isRuntimeShaderTrulySupported`。 */
fun isRuntimeShaderTrulySupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** 当前设备是否进入 lens 调用路径。本仓库没有教务的性能档位梯子，只看平台能力。 */
fun isRuntimeLensEnabled(): Boolean = isRuntimeShaderTrulySupported()

/**
 * 库的 lens 支持哪些形状。**这不是画质判据，是崩溃判据**（教务原文）：
 * `lens()` 只认连续曲率/圆角系形状，其它形状在 draw 阶段直接抛
 * `UnsupportedOperationException`，整个 App 闪退。教务删过这条判据一次，
 * `LiquidPicker` 的隐式曲面一上屏就崩，打开即闪退。
 *
 * 本仓库底栏只用 `CircleShape`（`CornerBasedShape`），判据保留原样防未来踩坑。
 */
fun isLensShapeSupported(shape: Shape): Boolean = shape is CornerBasedShape

/** 能不能开真透镜。形状那一项**必须**在（见 [isLensShapeSupported]），其余是纯算术护栏。 */
fun canUseLiquidLens(
    shape: Shape,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    minCornerRadiusPx: Float,
    minDimensionPx: Float,
): Boolean =
    isRuntimeLensEnabled() &&
        isLensShapeSupported(shape) &&
        refractionHeightPx.isFinite() &&
        refractionAmountPx.isFinite() &&
        minCornerRadiusPx.isFinite() &&
        minDimensionPx.isFinite() &&
        refractionHeightPx > 0f &&
        refractionAmountPx > 0f &&
        minCornerRadiusPx > 0f &&
        minDimensionPx > 0f &&
        refractionHeightPx <= minCornerRadiusPx &&
        refractionAmountPx <= minDimensionPx
