package dev.showcode.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.data.SyncResult
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.withTabular
import dev.showcode.ui.theme.SurfaceFloating
import dev.showcode.ui.theme.TabularNumbers

/** 胶囊本体高度。52dp 让它在 44dp 最小触控目标之上还留出一行字的余量。 */
val REFRESH_PILL_HEIGHT = 52.dp

private val REFRESH_PILL_BOTTOM = 12.dp

/**
 * 列表底部净空 = 胶囊高 + 底距 + 一个卡片间距。
 * 少了这一项，滚到底时最后一张取件点卡片会被胶囊永久压住——而那张卡往往是最新到的件。
 */
val REFRESH_PILL_CLEARANCE = REFRESH_PILL_HEIGHT + REFRESH_PILL_BOTTOM + 16.dp

/**
 * 浮动刷新胶囊：把「现在同步的是什么时候的数据」这件事一直摆在拇指边上。
 *
 * 它不隐藏、不折叠、不随滚动消失——首页的失效是静默的（权限掉了不会弹通知），
 * 所以入口必须是常驻的。文字用 tabular-nums，因为「3分钟前」会逐分钟变，
 * 数字宽度不齐会让整条胶囊左右晃。
 *
 * 三段话按优先级抢同一个位置：同步中 > 刚同步完的结果 > 上次同步时间。
 * 结果只露 [RESULT_VISIBLE_MS]，因为胶囊是常驻件，长期挂着一个数字会变成噪音。
 */
private const val RESULT_VISIBLE_MS = 8_000L

@Composable
fun RefreshPill(
    lastSyncAt: Long?,
    now: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    syncing: Boolean = false,
    result: SyncResult? = null,
    failed: Boolean = false,
) {
    val neverSynced = stringResource(R.string.home_never_synced)
    val freshResult = result?.takeIf { now - it.at <= RESULT_VISIBLE_MS }
    val whenText = when {
        syncing -> stringResource(R.string.home_syncing)
        failed -> stringResource(R.string.home_sync_failed)
        freshResult != null && freshResult.added > 0 ->
            stringResource(R.string.home_sync_added, freshResult.added)
        freshResult != null -> stringResource(R.string.home_sync_none)
        else -> lastSyncAt?.let { relativeTimeText(it, now) } ?: neverSynced
    }

    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = REFRESH_PILL_BOTTOM)
            .height(REFRESH_PILL_HEIGHT)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(SurfaceFloating)
            .border(1.dp, Hairline, CircleShape)
            .clickable(enabled = !syncing, onClick = onRefresh)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (failed) UrgencyDueSoon else Accent,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = whenText,
            style = ShowCodeText.Action.withTabular(),
            color = if (failed) UrgencyDueSoon else InkSecondary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(if (failed) R.string.home_stale_retry else R.string.refresh_action),
            style = ShowCodeText.Action,
            color = Ink,
        )
    }
}
