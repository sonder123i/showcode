package dev.showcode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.core.NotifyApp
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe

/**
 * 按 App 的通知开关。
 *
 * 这一屏必须先把它**做不到**的事说在前面，否则它会被读成反义：
 *
 * 1. **默认全开，所以这里一条噪音都不会少。** 它交出去的是「能关」，不是安静。
 *    不写这一句，用户开完这一屏会以为世界该安静了——而噪音一条没减是这一屏的正确行为。
 * 2. **只管通知，不管短信。** 同一个 App 常常两边都发；关掉这里不等于它的短信也不收，
 *    那是收容页旁边那条「不再显示此类」管的。
 * 3. **关掉期间推来的，永久收不回。** 通知没有收件箱可回填，错过就是错过，
 *    重新打开只救以后的那几条。这一句最不该省：它决定用户敢不敢按下开关。
 */
@Composable
fun NotifyAppsScreen(
    apps: List<NotifyApp>,
    now: Long,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.notify_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.notify_hint),
                    style = ShowCodeText.Meta,
                    color = InkMuted,
                )
            }
            if (apps.isEmpty()) {
                // 空态说的是「通知监听还没见过任何东西」，不是「这个功能没做」——
                // 而这一屏恰恰是用户怀疑自己没配好的时候会来的地方，两件事必须分得开。
                item {
                    Text(
                        text = stringResource(R.string.notify_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, ShowCodeShapes.Md)
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(16.dp),
                        style = ShowCodeText.Meta,
                        color = InkSecondary,
                    )
                }
            }
            items(apps, key = { it.packageName }) { app ->
                NotifyAppRow(app = app, now = now, onToggle = onToggle)
            }
            item {
                Text(
                    text = stringResource(R.string.notify_irreversible_hint),
                    style = ShowCodeText.Caption,
                    color = UrgencyDueSoon,
                )
            }
        }
    }
}

@Composable
private fun NotifyAppRow(app: NotifyApp, now: Long, onToggle: (String, Boolean) -> Unit) {
    /* 关掉一个**转发过代取件**的 App，实际关掉的是妈妈那条入口（story-7 的主路径）。
       所以那一档不是弹一句提示，而是把「确认」做成第二次点击：第一次只把那句话放大，
       改动本身仍然要人再点一次。重新打开没有代价，所以反悔一次点击就走完，不许拦。 */
    var confirming by rememberSaveable(app.packageName) { mutableStateOf(false) }
    val needsConfirm = app.forwardsFamily && !app.disabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, if (confirming) UrgencyDueSoon else Hairline, ShowCodeShapes.Md)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = app.label,
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Item,
                color = if (app.disabled) InkMuted else Ink,
            )
            Text(
                text = stringResource(if (app.disabled) R.string.notify_off else R.string.notify_on),
                modifier = Modifier
                    .clickable {
                        if (app.disabled) {
                            onToggle(app.packageName, true)
                        } else if (needsConfirm && !confirming) {
                            confirming = true
                        } else {
                            onToggle(app.packageName, false)
                        }
                    }
                    .background(
                        color = if (app.disabled) OnsiteSurface else UrgencySafe.copy(alpha = 0.12f),
                        shape = ShowCodeShapes.Sm,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = ShowCodeText.Action,
                color = if (app.disabled) InkMuted else UrgencySafe,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.notify_seen, app.seenCount, relativeTimeText(app.lastSeenAt, now)),
            style = ShowCodeText.Caption,
            color = InkMuted,
        )
        if (needsConfirm) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (confirming) R.string.notify_forward_confirm else R.string.notify_forward_warning
                ),
                style = ShowCodeText.Meta,
                color = UrgencyDueSoon,
            )
            if (confirming) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.notify_cancel),
                    modifier = Modifier
                        .clickable { confirming = false }
                        .padding(vertical = 4.dp),
                    style = ShowCodeText.Action,
                    color = InkMuted,
                )
            }
        }
    }
}
