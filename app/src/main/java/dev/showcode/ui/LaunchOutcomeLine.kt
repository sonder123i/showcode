package dev.showcode.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.showcode.R
import dev.showcode.core.LaunchOutcome
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.UrgencyDueSoon

/**
 * 「打开那家 App」的结局那一句话，四档共用一个落点。
 *
 * 常驻的一行，不是 toast：这个人手上可能正抱着两个包裹、屏幕上有水、身后有人催，
 * 一条一秒钟消失的提示他追不上——而追不上的提示等于没说。
 *
 * 这一格原来是出示屏自己写的一份（连字号一起）。身份码列表上那颗预置按钮会按出**同样四种**
 * 结局，所以把它抽成一份：同一种失败在两个屏上说不一样的话，是这个 App 里已经栽过的漂移。
 */
@Composable
fun LaunchOutcomeLine(
    outcome: LaunchOutcome?,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val text = when (outcome) {
        LaunchOutcome.Blocked -> stringResource(R.string.identity_launch_blocked)
        LaunchOutcome.NotInstalled -> stringResource(R.string.identity_launch_not_installed)
        LaunchOutcome.NoTarget -> stringResource(R.string.identity_launch_no_target)
        // 真起来了没什么好说的；没按过也不许说任何话。
        LaunchOutcome.Opened, null -> null
    } ?: return
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = ShowCodeText.Meta,
        color = UrgencyDueSoon,
        textAlign = textAlign,
    )
}
