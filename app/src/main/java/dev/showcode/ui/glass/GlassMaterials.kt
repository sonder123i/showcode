package dev.showcode.ui.glass

/**
 * 玻璃材质表。唯一权威源是教务 `ui/system/GlassMaterial.kt` 的 `GlassMaterials` 与 `GlassRecipe`
 * （.design-qa/DESIGN.md 里那张按**角色**而不是按组件分档的表）。
 *
 * 这里只搬本仓库真的有面的那些档。教务的角色表另有 Control / Modal / Interactive，
 * 等有面了再搬——留一个没人消费的 role，和「M3 给了 5 个容器 role、代码消费 0 个」是同一种毛病。
 *
 * 折射与色散在批 6 之前只由 33+ 的库 `lens()` 出；教务的 Navigation 轨道本来也是
 * 「blur 10 / 无色散 / 不折射」，所以轨道这一层两案完全一致。
 */
data class GlassRecipe(
    /**
     * blur 半径，**dp**。库的 `blur()` 收裸 Float，单位是 **px**——字节码里它直通
     * `BlurEffect(radiusX, radiusY)` → `RenderEffect.createBlurEffect`，没有换算。
     * 调用点必须 `.toPx()`。上一版把裸 10f 直接传进去（≈4dp），这是一处实打实的 bug。
     */
    val blurDp: Float,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
)

object GlassMaterials {

    /**
     * 教务那张表的 Navigation 行：blur 10 / 遮罩 0.16 / 描边 0.14。
     * 读它的一个要点：`surfaceAlpha` 是**遮罩** alpha，不是「玻璃有多白」——可读性来自
     * blur 把背后的东西雾化均匀，掩码只是把整体亮度拉回一点。
     */
    private val Navigation = GlassRecipe(
        blurDp = 10f,
        surfaceAlpha = 0.16f,
        borderAlpha = 0.14f,
    )

    /**
     * 高对比度文字下的改写（教务那套：遮罩 +0.20 上限 0.90、描边 +0.18 上限 0.60）。
     * 这条轴对本仓库比对教务更实在：底栏压着的是一条会滚动的取件码列表。
     */
    fun navigation(highTextContrast: Boolean): GlassRecipe =
        if (highTextContrast) {
            Navigation.copy(
                surfaceAlpha = (Navigation.surfaceAlpha + 0.20f).coerceAtMost(0.90f),
                borderAlpha = (Navigation.borderAlpha + 0.18f).coerceAtMost(0.60f),
            )
        } else {
            Navigation
        }

    /**
     * 有折射外观档的轨道容器色（教务 `CapsuleNavigationBar` 写死 `White @ 0.28`，
     * 注释「提浊：降低穿透内容对比度，深色文字经过栏后不再形成清晰污块」）。
     * role 表里的 0.16 是「玻璃有多透」的基础值，轨道这一档是它的**实际用法**。
     */
    const val NavTrackSurfaceAlpha = 0.28f

    /** 轨道高光/投影（教务 hasLensLook 分支 Highlight 0.16 / Shadow 0.08；无折射档两者都是 null）。 */
    const val NavTrackHighlightAlpha = 0.16f
    const val NavTrackShadowAlpha = 0.08f

    /** 无折射外观档的轨道（教务 `GlassRecipe.NavLegacy*`，cba2a09）：blur 8dp + FAFAFA @ 0.15，无高光无投影。 */
    const val NavLegacyTrackBlurDp = 8f
    const val NavLegacyTrackSurface = 0xFFFAFAFA
    const val NavLegacyTrackSurfaceAlpha = 0.15f

    /**
     * 选中胶囊的表面。⚠️ **修一处上一版引错的出处**：旧注释引的
     * `SelectedCapsuleSolidAlphaLight = 0.94` 在教务**零消费**——全仓 grep 只有定义，
     * 那是分段控件的常量（注释写「底栏与 segmented 共用」，但底栏根本没接）。
     * 教务底栏实际走的是 `NavSelectedSolid*` 这一组：
     * 有折射外观档白 **0.34**（按下降到 0.14 露出折射）、无折射档 **Black × 0.1**（按下淡出）。
     * 本仓库对号入座：33+ 的 lens 档走前者，其余走后者。
     */
    const val NavSelectedSolidAlpha = 0.34f
    const val NavSelectedGlassAlpha = 0.14f
    const val NavLegacyIndicatorAlpha = 0.1f
    const val NavLegacyIndicatorPressAlpha = 0.03f

    /** 指示胶囊的投影（教务 Shadow 0.10 + 按压 0.15）。批 3 接按压渐显，批 1 只给静止值。 */
    const val NavIndicatorShadowAlpha = 0.10f
}
