package dev.showcode.ui.onsite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.Urgency
import dev.showcode.core.staleDaysOf
import dev.showcode.core.urgencyOf
import dev.showcode.ui.copyCode
import dev.showcode.ui.deadlineText
import dev.showcode.ui.lockerLabelOf
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.rememberTickingNow
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.SurfaceInsetStrong
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import kotlinx.coroutines.delay

/** 平铺入口的件数门槛（与 H5 参照一致：>3 件时用户不想滑那么多次）。 */
private const val TILES_ENTRY_THRESHOLD = 3

/** 撤销窗口。现场误触成本极高（手抖、戴手套、屏幕有水、后面有人催），没有撤销的「已取」是危险的。 */
private const val UNDO_WINDOW_MS = 3_000L

/**
 * 现场模式（全屏出示）。story-4 —— 整个产品唯一不可妥协的一屏，
 * 其他 6 条故事都是为了让用户能走到这里。
 *
 * 使用环境极差：户外强光、单手、戴手套、屏幕有水、身后有人、注意力在柜机而不在手机上。
 * 所以这一屏里任何需要精确点击、需要读小字、需要等待转圈的东西都是失败。
 */
@Composable
fun OnsiteScreen(
    pointName: String,
    items: List<Parcel>,
    now: Long,
    isDemo: Boolean,
    focusParcelId: Long?,
    lastPickupAt: Long?,
    allPickedHere: Boolean,
    /** 代取件分组的名字，用户可改（story-7 AC2）。 */
    familyLabel: String,
    onBack: () -> Unit,
    onExitDemo: () -> Unit,
    onOpenTiles: (Long) -> Unit,
    onMarkPicked: (Parcel) -> Unit,
    onUndoPicked: (Parcel) -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    var group by remember { mutableStateOf(Owner.SELF) }
    var undoTarget by remember { mutableStateOf<Parcel?>(null) }
    var staleNoticeDismissed by remember { mutableStateOf(false) }

    val familyCount = items.count { it.owner == Owner.FAMILY }
    val selfCount = items.count { it.owner == Owner.SELF }
    val showGroupSwitch = familyCount > 0 && selfCount > 0
    // 代取件单独成组，轮播只在当前组内进行 → 天然不跨组（boundary-daqu 红线：
    // 在自己的码和家人的码之间迷失，会直接抵消「找得准」这个核心价值）
    val groupItems = remember(items, group, showGroupSwitch) {
        if (showGroupSwitch) items.filter { it.owner == group } else items
    }

    // 临期件需要逐秒变化的倒计时，所以有临期件时 1 秒一跳，否则 30 秒足够
    val hasDueSoon = groupItems.any { urgencyOf(it, now) == Urgency.DUE_SOON }
    val tickingNow = rememberTickingNow(if (hasDueSoon) 1_000L else 30_000L)

    val pagerState = rememberPagerState { groupItems.size }

    // 从平铺总览点回来的那一件可能属于另一个组（代取件），所以先切组。
    LaunchedEffect(focusParcelId) {
        val target = items.firstOrNull { it.id == focusParcelId } ?: return@LaunchedEffect
        if (showGroupSwitch && target.owner != group) group = target.owner
    }
    // 定位只跟 (group, focusParcelId) 走，**不能**跟列表长度走：
    // 点「已取」后列表会少一件，若把 size 当 key，这里就会滚回第 0 页，
    // 而正确的行为是保持索引不动——列表缩短一格，下一件自然滑到眼前。
    LaunchedEffect(group, focusParcelId) {
        val target = groupItems.indexOfFirst { it.id == focusParcelId }
        val page = if (target >= 0) target else 0
        if (pagerState.currentPage != page) pagerState.scrollToPage(page)
    }

    LaunchedEffect(undoTarget) {
        val target = undoTarget ?: return@LaunchedEffect
        delay(UNDO_WINDOW_MS)
        if (undoTarget == target) undoTarget = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
    ) {
        // 水印必须在内容底下：它是 60sp ExtraBold，压在「长按可复制」这类灰字上会把字糊掉。
        // 外层 Box 已经铺了不透明白底，所以放在第一个子节点就同时满足「看得见」和「不压字」。
        if (isDemo) DemoWatermark()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            OnsiteTopBar(pointName = pointName, isDemo = isDemo, onBack = onBack, onExitDemo = onExitDemo)

            if (showGroupSwitch) {
                GroupSwitch(
                    selfCount = selfCount,
                    familyCount = familyCount,
                    familyLabel = familyLabel,
                    selected = group,
                    onSelect = { group = it },
                )
            }

            if (groupItems.isEmpty()) {
                OnsiteEmpty(
                    allPickedHere = allPickedHere,
                    lastPickupAt = lastPickupAt,
                    now = now,
                    onBack = onBack,
                    modifier = Modifier.weight(1f),
                )
                // 取完最后一件时也必须能撤销：撤销条不能只活在「还有件」的分支里
                undoTarget?.let { target ->
                    UndoBar(isDemo = isDemo, onUndo = {
                        onUndoPicked(target)
                        undoTarget = null
                    })
                }
            } else {
                val page = pagerState.currentPage.coerceIn(0, groupItems.lastIndex)
                val current = groupItems[page]

                // error-stale-code：告知风险，但绝不阻断出示——他可能确实需要这个码
                val staleDays = staleDaysOf(current, tickingNow)
                if (staleDays > 1 && !staleNoticeDismissed) {
                    StaleCodeNotice(
                        days = staleDays,
                        showRefresh = !isDemo,
                        onDismiss = { staleNoticeDismissed = true },
                        onRefresh = onRefresh,
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { index ->
                    val parcel = groupItems.getOrElse(index) { return@HorizontalPager }
                    val urgency = urgencyOf(parcel, tickingNow)
                    OnsiteCodeBody(
                        parcel = parcel,
                        urgency = urgency,
                        lockerLabel = lockerLabelOf(parcel.pointKind),
                        deadlineText = deadlineText(parcel, urgency, tickingNow),
                        onCopyCode = { copyCode(context, parcel.code, R.string.onsite_copied) },
                    )
                }

                undoTarget?.let { target ->
                    UndoBar(isDemo = isDemo, onUndo = {
                        onUndoPicked(target)
                        undoTarget = null
                    })
                }

                PositionRow(
                    index = page,
                    total = groupItems.size,
                    showTilesEntry = groupItems.size > TILES_ENTRY_THRESHOLD,
                    onOpenTiles = { onOpenTiles(current.id) },
                )

                // 黑底白字，与整屏的纯黑 on 纯白一致：直射阳光下饱和蓝比黑更容易发灰
                PickedButton(onClick = {
                    // 视觉反馈必须和点击同一帧发生：状态改在父级同步完成，这里只负责起撤销窗口
                    undoTarget = current
                    onMarkPicked(current)
                })
            }
        }
    }
}

/**
 * 顶部条。刻意与 H5 参照有一处分歧：参照把点名截断到 190px，
 * 但 edge 的 boundary-long-text 明确要求「现场模式必须完整显示」——
 * 现场用户要靠完整名称确认「我来对地方了」。所以这里允许换行，不设 maxLines。
 */
@Composable
private fun OnsiteTopBar(pointName: String, isDemo: Boolean, onBack: () -> Unit, onExitDemo: () -> Unit) {
    Column {
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
                text = pointName,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnsiteInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(48.dp))
        }

        // 演示态退出条：常驻，退出后不残留任何示例数据
        if (isDemo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceInsetStrong)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onsite_demo_bar),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = OnsiteInk.copy(alpha = 0.6f),
                )
                Text(
                    text = stringResource(R.string.onsite_demo_exit),
                    modifier = Modifier
                        .clickable(onClick = onExitDemo)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentInk,
                )
            }
        }
    }
}

@Composable
private fun GroupSwitch(
    selfCount: Int,
    familyCount: Int,
    familyLabel: String,
    selected: Owner,
    onSelect: (Owner) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GroupTab(
            label = stringResource(R.string.onsite_group_self, selfCount),
            selected = selected == Owner.SELF,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(Owner.SELF) },
        )
        GroupTab(
            label = stringResource(R.string.onsite_group_family, familyLabel, familyCount),
            selected = selected == Owner.FAMILY,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(Owner.FAMILY) },
        )
    }
}

@Composable
private fun GroupTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = modifier
            .background(if (selected) OnsiteInk else SurfaceInsetStrong, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (selected) OnsiteSurface else OnsiteInk.copy(alpha = 0.6f),
    )
}

/** 该点无件：不能是空白屏——用户站在柜机前看到空白会恐慌，会怀疑自己记错了地方。 */
@Composable
private fun OnsiteEmpty(
    allPickedHere: Boolean,
    lastPickupAt: Long?,
    now: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (allPickedHere) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = UrgencySafe,
            )
        } else {
            Text(text = "📭", fontSize = 56.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (allPickedHere) R.string.onsite_all_done_title else R.string.onsite_empty_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OnsiteInk,
            textAlign = TextAlign.Center,
        )
        // 用最近一次取件时间做锚定，把「是不是走错地方了」这个念头按住
        if (lastPickupAt != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onsite_empty_anchor, relativeTimeText(lastPickupAt, now)),
                fontSize = 14.sp,
                color = OnsiteInk.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(OnsiteInk, RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.onsite_back_home),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnsiteSurface,
            )
        }
    }
}

/**
 * loading-submit 红线：点「已取」后立即有视觉反馈，不等任何异步。
 * 现场用户马上要放下手机去拿包裹，任何 spinner 都是失败。
 * 位置在「已取」按钮之上而不是盖住它——3 秒里挡住按钮，用户就没法连着取下一件。
 */
@Composable
private fun UndoBar(isDemo: Boolean, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(OnsiteInk, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (isDemo) R.string.onsite_demo_toast else R.string.onsite_marked_picked),
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnsiteSurface,
        )
        if (!isDemo) {
            Text(
                text = stringResource(R.string.onsite_undo),
                modifier = Modifier
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnsiteSurface,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun PositionRow(index: Int, total: Int, showTilesEntry: Boolean, onOpenTiles: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.onsite_position, index + 1, total),
            fontSize = 14.sp,
            color = OnsiteInk.copy(alpha = 0.5f),
            style = TabularNumbers,
        )
        if (showTilesEntry) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpenTiles)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onsite_tiles_entry),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentInk,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AccentInk,
                )
            }
        }
    }
}

/** 「已取」：底部安全区之上、单手拇指可达、64dp 高（≥56dp 硬标准，手套 + 水屏容错）。 */
@Composable
private fun PickedButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
            .height(64.dp)
            .background(OnsiteInk, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp), tint = OnsiteSurface)
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(R.string.onsite_picked), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnsiteSurface)
    }
}

@Composable
private fun StaleCodeNotice(days: Int, showRefresh: Boolean, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoon.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.onsite_stale_code, days),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = OnsiteInk,
        )
        if (showRefresh) {
            Text(
                text = stringResource(R.string.refresh_action),
                modifier = Modifier
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OnsiteInk,
            )
        }
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
            fontSize = 13.sp,
            color = OnsiteInk.copy(alpha = 0.5f),
        )
    }
}

/**
 * 演示态水印。与首页示例卡片用同一套视觉语言，
 * 免得用户把演示数据当成真数据——这是很容易犯的错。
 * 不带任何 pointer 修饰符，所以左右滑的手势会穿透到下面的轮播。
 */
@Composable
internal fun DemoWatermark() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.sample_badge),
            modifier = Modifier.rotate(-24f),
            fontSize = 60.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black.copy(alpha = 0.06f),
            letterSpacing = 0.2.em,
        )
    }
}
