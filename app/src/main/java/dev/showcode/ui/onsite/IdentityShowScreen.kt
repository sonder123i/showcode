package dev.showcode.ui.onsite

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.IdentityCode
import dev.showcode.core.IdentityCodes
import dev.showcode.core.IdentityKind
import dev.showcode.core.LaunchOutcome
import dev.showcode.core.qr.Qr
import dev.showcode.ui.copyCode
import dev.showcode.ui.identity.QrMatrixCanvas
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencyDueSoon

/**
 * 出示一张身份码（驿站自助机、柜机寄件那种要扫一下的东西）。
 *
 * 这一屏借用现场模式那套约束，因为它们的使用环境是同一个：户外强光、单手、身后有人催，
 * 而**码和扫码窗之间只有那 20 厘米**。所以二维码占掉整屏能占的最大方块，
 * 其余一切都排在它下面，包括那串给人核对用的码值。
 */
@Composable
fun IdentityShowScreen(
    code: IdentityCode,
    appInstalled: Boolean,
    launch: LaunchOutcome?,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val context = LocalContext.current
    // 入口那一类没有码可编：它不是「画不出码」，是「本来就没有码」。两件事不能共用一句报错。
    val isEntry = code.kind == IdentityKind.ENTRY
    val matrix = remember(code.value, isEntry) { if (isEntry) null else Qr.of(code.value) }
    val fontScale = LocalDensity.current.fontScale
    /* 动态码默认收起来，点开才画。按码分键：上一张手动开过，换一张不该继承那个决定——
       这张屏的每一次出示都是一次独立的判断。 */
    var revealed by rememberSaveable(code.id) { mutableStateOf(false) }

    // 亮到最高 + 不许熄屏。柜机那个相机把屏幕当一个自发光方块测光，
    // 用户平时留在 30％ 亮度时，同一张码要重试好几次——而那好几次是站在风里过的。
    KeepScreenBright()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onsite_back))
            }
            Text(
                text = code.label,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = OnsiteInk,
            )
        }

        val stale = IdentityCodes.shouldWarnStale(code)
        val collapsedStale = IdentityCodes.shouldCollapseQr(code, revealed)

        if (stale && !collapsedStale) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UrgencyDueSoon.copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = UrgencyDueSoon)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.identity_stale_warning),
                    fontSize = 13.sp,
                    color = OnsiteInk,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isEntry) {
                // 入口没有码可画。这一句的全部作用是**别让人以为这里少显示了什么**，
                // 并把注意力交给下面那颗按钮。
                Text(
                    text = stringResource(R.string.identity_entry_hint),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnsiteInk,
                    textAlign = TextAlign.Center,
                )
            } else if (collapsedStale) {
                // 会话级动态码默认不占满这一屏：它几乎一定已经作废，
                // 而一张「看起来完整」的废码是这一屏能造成的最坏的误导。
                // 码值本身仍然在下面那一段（人眼核对靠它，那里走的是现场模式那套字号）。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.identity_stale_collapsed),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnsiteInk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.identity_reveal_qr),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.dp, OnsiteInk, RoundedCornerShape(14.dp))
                            .clickable { revealed = true },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnsiteInk,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (matrix == null) {
                // 存进去时编得出来、现在编不出来，只可能是版本上限以后被调低了。
                // 这时候**不能画一张假码**——画不出就是画不出，把那串字符留给人工输入。
                Text(
                    text = stringResource(R.string.identity_cannot_render),
                    fontSize = 15.sp,
                    color = UrgencyDueSoon,
                    textAlign = TextAlign.Center,
                )
            } else {
                // 方块必须方：竖屏里高度富余，直接 fillMaxWidth 会让上下留白而码是正方形，
                // 但横屏（有人把手机横过来举着）时不卡尺寸就会画成一个压扁的矩形——扫不出来。
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val side = minOf(maxWidth, maxHeight)
                    Box(
                        modifier = Modifier
                            .size(side)
                            .background(OnsiteSurface, RoundedCornerShape(0.dp)),
                    ) {
                        QrMatrixCanvas(matrix = matrix, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 码值走现场模式那套防溢出字号，不另写一套：一维码时代的教训就是两套规则会漂移。
            if (!isEntry) BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val viewportWidthDp = maxWidth.value
                Text(
                    text = code.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyCode(context, code.value, R.string.identity_copied) },
                    fontSize = codeFontSize(code.value, hasLocker = true, viewportWidthDp, fontScale),
                    fontWeight = FontWeight.Bold,
                    color = OnsiteInk,
                    fontFamily = FontFamily.Monospace,
                    style = TabularNumbers,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            if (!isEntry) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.identity_copy_hint),
                    fontSize = 12.sp,
                    color = OnsiteInk.copy(alpha = 0.55f),
                )
            }

            code.note?.let { note ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = note,
                    fontSize = 13.sp,
                    color = OnsiteInk.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }

            /* 降级路：会话码在这一屏救不了自己，只能把人送到能救他的那个 App 去。
               这颗按钮**不按装没装隐藏**——藏掉它等于把这条唯一的退路一起藏了，
               用户会对着一个什么都没有的屏幕发呆；没装就照实说没装。 */
            if (code.appPackage != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(OnsiteInk, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenApp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnsiteSurface)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (appInstalled) R.string.identity_open_app else R.string.identity_open_app_missing
                        ),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnsiteSurface,
                    )
                }
                when (launch) {
                    // 「点了没反应」是竞品 issue #39 的全部内容，而它真正的答案是系统里的一个开关。
                    // 这句话必须在这屏上能看见，不该像他们那样只在 issue 回复里流传。
                    LaunchOutcome.Blocked -> LaunchLine(stringResource(R.string.identity_launch_blocked))
                    LaunchOutcome.NotInstalled -> LaunchLine(stringResource(R.string.identity_launch_not_installed))
                    LaunchOutcome.NoTarget -> LaunchLine(stringResource(R.string.identity_launch_no_target))
                    // 打开了就没什么好说的；没按过也什么都不说。
                    LaunchOutcome.Opened, null -> Unit
                }
            }
        }
    }
}

/**
 * 跳转结局那一句话。
 *
 * 刻意做成常驻的一行，不是 toast：这个人手上可能正抱着两个包裹、屏幕上有水、身后有人催，
 * 一条一秒钟消失的提示他追不上，追不上的提示等于没说。
 */
@Composable
private fun LaunchLine(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        fontSize = 13.sp,
        color = UrgencyDueSoon,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 抬亮度 + 不许熄屏，离开时原样还回去。
 *
 * 柜机那个相机把屏幕当一个自发光方块测光，用户平时留在 30％ 时同一张码要重试好几次。
 * 但**不是拉到 1.0**：扫码要的是对比不是最大亮度，夜里在楼下取件时 1.0 是拿屏幕照人眼。
 * 所以是「抬到不低于 0.7，本来就更高的不降下来」。
 *
 * 写在 DisposableEffect 而不是别的地方，是因为「离开这一屏」有三条路：
 * 返回键、Home、进程被杀。前两条都必须恢复用户自己的亮度，第三条由系统接手。
 */
@Composable
private fun KeepScreenBright() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.let { it.attributes = it.attributes.apply { screenBrightness = IdentityCodes.liftedBrightness(previous) } }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { it.attributes = it.attributes.apply { screenBrightness = previous ?: -1f } }
        }
    }
}
