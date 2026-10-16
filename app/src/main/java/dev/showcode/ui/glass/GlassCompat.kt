package dev.showcode.ui.glass

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 底栏能拿到哪一档材质。名字对着教务 `ui/system/GlassCompat.kt` 那套梯子，档数少了两个，
 * 少的原因写在下面那条注释里——不是漏了。
 */
enum class GlassTier {
    /** 不透明底栏。今天的观感，也是所有降级路径的终点。 */
    OPAQUE,

    /** 真玻璃：blur 折背景 + 一层遮罩 + 浅色描边。 */
    BACKDROP,
}

object GlassCompat {

    /** blur 来自 RenderEffect，API 31 才有；33 才是 lens（本仓库不用 lens）。 */
    const val MIN_BACKDROP_SDK = Build.VERSION_CODES.S

    /**
     * 判决顺序不可改，和教务那套一样：用户开关 → 平台能力 → 真失败留下的记号 → 玻璃。
     *
     * ⚠️ 教务的梯子有四个材质档（Material / StaticGlass / Backdrop / DynamicLens），这里只有两个：
     * 「不透明」和「真玻璃」。砍掉的是 `StaticGlass`（有透明但不模糊的那一档）——本仓库 L0 当年
     * 否掉「半透明填充」的理由正是每张卡上的取件码会直接从浮层底下透过来，而底栏压着的就是
     * 那条列表。也就是说 `StaticGlass` 在这里没有面可放，不是还没做。
     * `DynamicLens` 没搬的理由见 GlassMaterials 顶部。
     */
    fun tier(enabledByUser: Boolean, sdkInt: Int, backdropAllowed: Boolean): GlassTier = when {
        !enabledByUser -> GlassTier.OPAQUE
        sdkInt < MIN_BACKDROP_SDK -> GlassTier.OPAQUE
        !backdropAllowed -> GlassTier.OPAQUE
        else -> GlassTier.BACKDROP
    }

    /**
     * 这台设备出不出**折射外观**。33+ 的 lens 由 backdrop 库出；批 6 把教务的离屏 GL
     * 接上后，31/32 也算进来（教务的判据 `hasLensLook = hasRealLens || useOffscreenLens`）。
     *
     * 它决定的是一组几何与材质：轨道高 72/62 vs 64/58、胶囊白 0.34 vs Black×0.1、
     * 轨道有没有高光/投影。教务的注释把因果写得很直白：「有真折射就给得开」。
     */
    fun lensLook(tier: GlassTier, sdkInt: Int): Boolean =
        tier == GlassTier.BACKDROP && sdkInt >= Build.VERSION_CODES.TIRAMISU
}

private const val HIGH_TEXT_CONTRAST_KEY = "high_text_contrast_enabled"

/**
 * 系统里的「高对比度文字」。
 *
 * 直接读那个 Secure 键而不是走 `AccessibilityManager.isHighTextContrastEnabled()`：两者的数据源
 * 本来就是同一个键，而下面那条 ContentObserver 也正注册在它上面——读写同一个源，才不会出现
 * 「监听的是 A、读的是 B」这种只在特定 ROM 上出现的鬼故事。
 *
 * 教务那边同样是实时读系统开关，这里照做：用户从系统设置里打开它、再切回本 App 时，
 * 进程往往还活着，启动时读一次的快照会停在旧值上。
 */
@Composable
fun rememberHighTextContrast(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = Settings.Secure.getInt(resolver, HIGH_TEXT_CONTRAST_KEY, 0) == 1
    var high by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                high = read()
            }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST_KEY), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return high
}