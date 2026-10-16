package dev.showcode.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.MINUTE_MS
import dev.showcode.core.HealthIssue
import dev.showcode.core.HealthReport
import dev.showcode.core.Parcel
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.SuspectPair
import dev.showcode.core.SearchWindow
import dev.showcode.core.Urgency
import dev.showcode.core.searchParcels
import dev.showcode.core.urgencyOf
import dev.showcode.data.SyncResult
import dev.showcode.ui.closingLabel
import dev.showcode.ui.URGENCY_DUE_SOON_ICON
import dev.showcode.ui.navBarClearance
import dev.showcode.ui.pointIconOf
import dev.showcode.ui.pointKindLabel
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.BorderDueSoon
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkWatermark
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.withTabular
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencyAtRisk
import dev.showcode.ui.theme.UrgencyAtRiskTint
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyDueSoonTint
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.UrgencySafeTint
import dev.showcode.ui.points.MergeUndoBar
import dev.showcode.ui.points.SuspectHintCard
import dev.showcode.ui.urgencyColor
import dev.showcode.ui.urgencyLabel
import kotlinx.coroutines.delay

/**
 * 首页的三种空态。edge meta-3 的红线：本产品有 6 种语义完全不同的空态，绝不可共用文案。
 * 这里出现的两种语义正好相反——FIRST_TIME 是「还没开始」，ALL_DONE 是「已经干完了」，
 * 后者是成就不是缺失，所以它是绿色的成功态。
 */
enum class HomeEmpty { NONE, FIRST_TIME, ALL_DONE }

data class HomeUiState(
    val loaded: Boolean,
    val groups: List<PickupPointGroup>,
    val empty: HomeEmpty,
    val monthlyPicked: Int,
    val lastSyncAt: Long?,
    /** 正在读收件箱。胶囊要当场说出来，否则用户以为没点上会连着点。 */
    val syncing: Boolean = false,
    /** 最近一次刷新的结果。只在结束后的一小段时间里显示。 */
    val lastSyncResult: SyncResult? = null,
    /** 读短信库失败。这时 lastSyncAt 故意不更新，所以红条必须立刻出现而不是等两小时。 */
    val lastSyncFailed: Boolean = false,
    /** 首页只放入口行与计数；完整列表住在未识别收容页（story-3 AC3）。 */
    val unrecognizedCount: Int,
    /** 最近标记为已取的件。误触超过 3 秒撤销窗口之后，这里是唯一的补救出口。 */
    val recentlyPicked: List<Parcel>,
    /** 收到第一个取件点后才出现的反向绑定横幅（story-3 AC5）。 */
    val showWidgetHint: Boolean,
    val smsPermissionGranted: Boolean,
    val notificationAccessGranted: Boolean,
    /** 疑似重复的一对（story-6 AC4）：只问一句，绝不自动合并。 */
    val suspect: SuspectPair? = null,
    /**
     * 已取走的件。检索用它回答空结果的两种：
     * 「没有这串」和「这串你今天已经取过了」是两句完全不同的话，而前者会让人白跑一趟。
     */
    val pickedParcels: List<Parcel> = emptyList(),
)

/** 数据超过这个时长没同步过就算陈旧。陈旧的码比没有码更危险。 */
private const val STALE_THRESHOLD_MS = 120 * MINUTE_MS

/** 卡片内最多列出的件数，多出来的收成「+N」。卡片高度因此不会随件数撑破布局。 */
private const val CARD_ITEM_LIMIT = 3

/** 卡片内一行的固定高度：有柜号与没柜号的行必须一样高，否则卡片高度会随数据抖动。 */
private val CARD_ROW_HEIGHT = 30.dp

/**
 * 首页 = 管理模式。第一级是取件点，不是包裹（Story 2 AC2）。
 * 排序由 [dev.showcode.core.groupByPoint] 决定：紧急度优先，件数只是次级排序。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    now: Long,
    sampleGroups: List<PickupPointGroup>,
    onOpenPoint: (String) -> Unit,
    onOpenDemo: (String) -> Unit,
    onRestoreParcel: (Parcel) -> Unit,
    report: HealthReport,
    onOpenSettings: () -> Unit,
    onFixWorst: () -> Unit,
    onOpenUnrecognized: () -> Unit,
    onManualImport: () -> Unit,
    onDismissWidgetHint: () -> Unit,
    onPinWidget: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRefresh: () -> Unit,
    onManagePoint: (PickupPointGroup) -> Unit,
    onSuspectYes: () -> Unit,
    onSuspectNo: () -> Unit,
    mergeUndoName: String?,
    onUndoMerge: () -> Unit,
) {
    /* 检索的三个状态留在这一屏，没有提到 Root：它是「此刻正在找东西」这种临时意图，
       切去现场模式再回来还挂着搜索框，会让人以为屏上少掉的件被 App 藏起来了。 */
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var window by remember { mutableStateOf(SearchWindow.ALL) }

    // 只选了窗（没打字）也算在筛；光打开搜索框但什么都不选，列表保持原样。
    val filtering = searchOpen && (query.isNotBlank() || window != SearchWindow.ALL)
    val result = if (searchOpen) searchParcels(state.groups, state.pickedParcels, query, window, now) else null
    val shownGroups = result?.groups ?: state.groups

    /* 陈旧提示对任何一条收录通道都成立。以前它只被短信权限门住，
       于是「拒了短信、只开通知监听」这个完全合法的配置下，用户永远看不到「可能不是最新」。 */
    val isStale = state.lastSyncFailed ||
        ((state.smsPermissionGranted || state.notificationAccessGranted) &&
            (state.lastSyncAt == null || now - state.lastSyncAt > STALE_THRESHOLD_MS))

    // 底色与系统栏内边距住在这儿。浮动胶囊搬走之后它只剩一个子层，
    // 但那三条修饰符挪进 Column 要把整屏重排一遍缩进——为一个空壳改动两百行不值当。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = ShowCodeText.Headline,
                        color = Ink,
                    )
                    // 胶囊搬到这里来了：底栏要占的是它那个位置，而这句话不能跟着一起没了。
                    SyncLine(state = state, now = now)
                }
                // 一件都没有时不给这颗按钮：那时候搜索框不是路，是一条死路。
                if (state.groups.isNotEmpty()) {
                    IconButton(
                        onClick = { searchOpen = !searchOpen },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (searchOpen) R.string.home_search_close else R.string.home_search,
                            ),
                            tint = if (searchOpen) Ink else InkSecondary,
                        )
                    }
                }
                // 顶栏不再有那颗设置图标：设置现在是底栏三颗键之一，
                // 同一屏上留两个入口会让人怀疑它们去的是不是同一个地方。
            }

            // permission-health-bar：跨屏常驻、不可被滚动隐藏——它挂在 LazyColumn 外面。
            // 权限失效是静默的，用户不会收到系统通知，只会觉得「最近好像没新快递」。
            HealthBar(report = report, now = now, lastSyncAt = state.lastSyncAt, onFix = onFixWorst, onView = onOpenSettings)

            // error-stale-data：不会被滚动隐藏——它挂在 LazyColumn 外面
            if (isStale) {
                StaleBar(
                    failed = state.lastSyncFailed,
                    lastSyncAt = state.lastSyncAt,
                    now = now,
                    onRefresh = onRefresh,
                )
            }

            /* 合并后的 5 秒撤销（AC2 的「立刻有一个明显的撤销入口」）。
               和权限条一样住在 LazyColumn 外面：一个能被滚走的反悔入口等于没有反悔。 */
            mergeUndoName?.let { MergeUndoBar(keptName = it, onUndo = onUndoMerge) }

            /* 挂在 LazyColumn 外面，和权限条同一个理由：会随列表滚走的控件等于没有。
               搜索框尤其如此——手指还在打字，框自己往上跑出可视区，用户只会以为 App 卡了。 */
            if (searchOpen) {
                SearchRow(
                    query = query,
                    window = window,
                    onQuery = { query = it },
                    onWindow = { window = it },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBarClearance()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.loaded) return@LazyColumn

                if (state.empty == HomeEmpty.FIRST_TIME) {
                    item {
                        // CTA 进排序最靠前那个点：一进演示就该看到最大的那个码，
                        // story-1 的风险写得很清楚——演示必须在 5 秒内让人看到它。
                        FirstTimeHeader(onOpenDemo = { sampleGroups.firstOrNull()?.let { onOpenDemo(it.key) } })
                    }
                    items(sampleGroups, key = { "sample-${it.key}" }) { group ->
                        SamplePointCard(group = group, now = now, onClick = { onOpenDemo(group.key) })
                    }
                    item {
                        Text(
                            text = stringResource(R.string.home_empty_first_time_hint),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(shownGroups, key = { it.key }) { group ->
                        val hint = state.suspect
                        /* 提示插在两张卡里更紧急那张的上方：它讲的是「这两张是同一件事」，
                           放在其中任何一张上面都比放在列表尾巴上读得懂。 */
                        if (hint != null && hint.first.key == group.key) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SuspectHintCard(pair = hint, onYes = onSuspectYes, onNo = onSuspectNo)
                                PointCard(
                                    group = group,
                                    now = now,
                                    onClick = { onOpenPoint(group.key) },
                                    onManage = { onManagePoint(group) },
                                )
                            }
                        } else {
                            PointCard(
                                group = group,
                                now = now,
                                onClick = { onOpenPoint(group.key) },
                                onManage = { onManagePoint(group) },
                            )
                        }
                    }
                    // 空态、补救出口、招揽横幅这些是「没人找东西时」的路；正在筛的时候它们是噪声。
                    if (!filtering) {
                        if (state.empty == HomeEmpty.ALL_DONE) {
                            item { AllDoneCard(monthlyPicked = state.monthlyPicked) }
                        }

                        // 误触「已取」超过 3 秒撤销窗口之后的唯一补救出口。
                        // 设计里点名它落在「首页 / 历史」，批 1 还没有历史页，所以落在首页。
                        if (state.recentlyPicked.isNotEmpty()) {
                            item { RecentlyPickedSection(parcels = state.recentlyPicked, onRestore = onRestoreParcel) }
                        }
                    }

                    if (filtering && shownGroups.isEmpty()) {
                        item {
                            val picked = result?.pickedMatches ?: 0
                            Text(
                                text = when {
                                    // 「那串你可能已经取过了」只在人真的打了字时才说：
                                    // 判据那边空白查询就不报已取，这里反过来造一句也是空话。
                                    picked > 0 -> stringResource(R.string.home_search_miss_picked, query.trim(), picked)
                                    query.isBlank() -> stringResource(R.string.home_search_miss_window)
                                    else -> stringResource(R.string.home_search_miss, query.trim())
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceCard, ShowCodeShapes.Md)
                                    .padding(16.dp),
                                style = ShowCodeText.Meta,
                                color = InkSecondary,
                            )
                        }
                    }
                }

                // 解析失败必须显性化（story-2 的 risk）：首页给入口行，完整列表在收容页。
                // 这一行**不跟着筛选让位**——「你没搜到」不等于「没有东西没认出来」。
                if (state.unrecognizedCount > 0) {
                    item { UnrecognizedLinkRow(count = state.unrecognizedCount, onOpen = onOpenUnrecognized) }
                }

                if (!filtering) {
                    // story-3 AC5：收到第一个取件点后才提示反向绑定。落点在 App 内横幅而不是组件——
                    // 组件是全产品空间最紧张的表面，它必须永远保住「点名」那一行
                    // （stories.json ac_amendment 的回归教训）。
                    if (state.showWidgetHint) {
                        item { WidgetHintBanner(onPin = onPinWidget, onDismiss = onDismissWidgetHint) }
                    }

                    if (!state.smsPermissionGranted) {
                        item {
                            EnableIngestCard(
                                title = stringResource(R.string.home_enable_ingest),
                                body = stringResource(R.string.home_enable_ingest_hint),
                                onClick = onRequestSmsPermission,
                            )
                        }
                    } else if (!state.notificationAccessGranted) {
                        item {
                            EnableIngestCard(
                                title = stringResource(R.string.home_enable_notification),
                                body = stringResource(R.string.home_enable_notification_hint),
                                onClick = onOpenNotificationSettings,
                            )
                        }
                    }
                }

                // 手动导入是「拒绝短信权限也能用」的一等退路（edge s1 permission-sms-denied），
                // 引导结束之后也必须常驻在列表末尾，**检索时也不藏**：
                // 手上有条码而 App 认不出来的那一刻，正是它最值钱的时候。
                item {
                    Text(
                        text = stringResource(R.string.home_manual_entry),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManualImport)
                            .padding(vertical = 10.dp),
                        style = ShowCodeText.Body,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 顶栏那行小字：现在看到的是什么时候的数据。
 *
 * 它以前住在底部那颗胶囊里，还兼作「立即刷新」的按钮。首页改成每次进入自动刷之后按钮
 * 没必要了，这句话必须留下——自动的东西失效方式是静默的（权限被 ROM 收回不会弹通知），
 * 用户得能一眼分清「这是三分钟前的数据」和「今天根本没刷成功」。
 *
 * 刚刷完那句只露 [SYNC_RESULT_MS]，且不拿页面那个 30 秒一跳的 `now` 比：那个刻度是给
 * 倒计时用的，用它这条会时准时不准——自动刷新之后这是首页唯一的「刷过了」的证据，不能飘。
 */
private const val SYNC_RESULT_MS = 8_000L

@Composable
private fun SyncLine(state: HomeUiState, now: Long) {
    // 没加载完不出声：那时「还没同步过」是把一次正常的读库演成一件缺了什么的事。
    if (!state.loaded) return
    val result = state.lastSyncResult
    val resultAt = result?.at
    var showResult by remember(resultAt) { mutableStateOf(result != null) }
    LaunchedEffect(resultAt) {
        if (resultAt != null) {
            delay(SYNC_RESULT_MS)
            showResult = false
        }
    }
    val text = when {
        state.syncing -> stringResource(R.string.home_syncing)
        state.lastSyncFailed -> stringResource(R.string.home_sync_failed)
        showResult && result != null && result.added > 0 -> stringResource(R.string.home_sync_added, result.added)
        showResult && result != null -> stringResource(R.string.home_sync_none)
        else -> state.lastSyncAt?.let {
            stringResource(R.string.home_synced_at, relativeTimeText(it, now))
        } ?: stringResource(R.string.home_never_synced)
    }
    Text(
        text = text,
        style = ShowCodeText.Caption,
        color = if (state.lastSyncFailed) UrgencyDueSoon else InkMuted,
    )
}

/**
 * 搜索那一行：一个输入框 + 三个互斥的时间窗。
 *
 * 窗做成一排单选而不是可叠加的开关，因为首页只有一个语义——「现在要去看哪些件」。
 * 叠成「今天到 且 临期 且 含已取」那种组合器，是档案柜的用法，不是作战面板的。
 */
@Composable
private fun SearchRow(
    query: String,
    window: SearchWindow,
    onQuery: (String) -> Unit,
    onWindow: (SearchWindow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.home_search_hint)) },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_search_clear),
                            tint = InkMuted,
                        )
                    }
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowChip(R.string.home_window_all, window == SearchWindow.ALL) { onWindow(SearchWindow.ALL) }
            WindowChip(R.string.home_window_urgent, window == SearchWindow.URGENT) { onWindow(SearchWindow.URGENT) }
            WindowChip(R.string.home_window_today, window == SearchWindow.TODAY) { onWindow(SearchWindow.TODAY) }
        }
    }
}

@Composable
private fun WindowChip(@StringRes labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = stringResource(labelRes),
        modifier = Modifier
            .background(if (selected) AccentTint else SurfaceCard, ShowCodeShapes.Sm)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Ink else Hairline,
                shape = ShowCodeShapes.Sm,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = ShowCodeText.Action,
        color = if (selected) Ink else InkMuted,
    )
}

/**
 * 权限健康常驻横条（story-5 / edge permission-health-bar）。
 * 全部正常时**完全不出现**；出现时只报最严重一项 + 「还有 N 项」，
 * 点整条进体检表，[去开启] 直接修最严重那项。
 */
@Composable
private fun HealthBar(
    report: HealthReport,
    now: Long,
    lastSyncAt: Long?,
    onFix: () -> Unit,
    onView: () -> Unit,
) {
    val worst = report.worst ?: return
    val neverSynced = stringResource(R.string.home_never_synced)
    val text = when (worst) {
        HealthIssue.SMS_DENIED -> stringResource(R.string.health_bar_sms)
        HealthIssue.NOTIFICATION_DENIED -> stringResource(R.string.health_bar_notification)
        HealthIssue.BACKGROUND_SUSPECTED -> stringResource(
            R.string.health_bar_background,
            lastSyncAt?.let { relativeTimeText(it, now) } ?: neverSynced,
        )
        // 小米项只进体检表，不进横条：它无法程序化判定，常驻报警会变成狼来了
        HealthIssue.MIUI_SMS_CLASS -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyAtRiskTint)
            .clickable(onClick = onView)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyAtRisk)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = ShowCodeText.Action, color = Ink)
            if (report.restCount > 0) {
                Text(
                    text = stringResource(R.string.health_bar_more, report.restCount),
                    style = ShowCodeText.Micro,
                    color = InkMuted,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.health_bar_view),
            modifier = Modifier
                .clickable(onClick = onFix)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            style = ShowCodeText.Action,
            color = UrgencyAtRisk,
        )
    }
}

@Composable
private fun StaleBar(failed: Boolean, lastSyncAt: Long?, now: Long, onRefresh: () -> Unit) {
    val neverSynced = stringResource(R.string.home_never_synced)
    val whenText = lastSyncAt?.let { relativeTimeText(it, now) } ?: neverSynced
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoonTint)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (failed) stringResource(R.string.home_stale_failed)
            else stringResource(R.string.home_stale, whenText),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Meta,
            color = Ink,
        )
        Text(
            text = stringResource(if (failed) R.string.home_stale_retry else R.string.refresh_action),
            modifier = Modifier
                .clickable(onClick = onRefresh)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            style = ShowCodeText.Action,
            color = Ink,
        )
    }
}

/** 取件点卡片：首页的一级公民。整张卡片点一下就进该点的现场模式。 */
@Composable
private fun PointCard(
    group: PickupPointGroup,
    now: Long,
    onClick: () -> Unit,
    /* 示例卡不带管理入口：演示数据不落库，合并一个假取件点会造出一条真登记。 */
    onManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val kindText = pointKindLabel(group.kind)
    val closingText = group.closesAtMinuteOfDay?.let { closingLabel(it) }
    val urgencyText = if (group.worst == Urgency.SAFE) null else urgencyLabel(group.worst)
    val subtitle = listOfNotNull(kindText, closingText, urgencyText).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // 色条：首页唯一的颜色编码位置。颜色之外永远有文字冗余（brief 的人群适配策略）
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(40.dp)
                .background(urgencyColor(group.worst), ShowCodeShapes.Pill)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型锚点：柜机要念格口、驿站要等关门，现场要做的动作不同，
                // 而这三类取件点在短信里长得几乎一样（都叫「XX驿站」「XX店」）。先给一个图形。
                Icon(
                    painter = painterResource(id = pointIconOf(group.kind)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = InkSecondary,
                )
                Spacer(Modifier.width(8.dp))
                // boundary-long-text：首页截断，现场模式完整显示——这是两个模式的真实分歧
                Text(
                    text = group.name,
                    modifier = Modifier.weight(1f),
                    style = ShowCodeText.Section,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.worst == Urgency.DUE_SOON) {
                    // 临期档以前一个图形都没有，而它恰恰是最需要被一眼认出的那一档。
                    // 安全用已有的勾、过期风险用已有的告警三角，不在这里重复造。
                    Icon(
                        painter = painterResource(id = URGENCY_DUE_SOON_ICON),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = UrgencyDueSoon,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = group.items.size.toString(),
                    style = ShowCodeText.Count,
                    color = Ink,
                )
                Text(
                    text = stringResource(R.string.home_count_unit),
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
                /* 管理入口必须另给一个角：点卡片直进现场模式那个 1 步承诺
                   不能被「合并取件点」这种操作污染。 */
                onManage?.let { manage ->
                    IconButton(
                        onClick = manage,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.home_manage),
                            tint = InkMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = ShowCodeText.Caption,
                color = if (group.worst == Urgency.SAFE) InkMuted else urgencyColor(group.worst),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            group.items.take(CARD_ITEM_LIMIT).forEach { parcel ->
                CardParcelRow(parcel = parcel, now = now)
            }
            // boundary-overflow：多出来的收成角标，不撑破卡片高度
            val rest = group.items.size - CARD_ITEM_LIMIT
            if (rest > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_ROW_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_card_overflow, rest),
                        style = ShowCodeText.Caption.withTabular(),
                        color = InkMuted,
                    )
                }
            }
        }
    }
}

/**
 * 卡片内的一件。Story 2 AC4：柜号紧贴取件码；没有柜号时那一块整个消失、
 * 取件码变得更大——不留空位，也不显示「--」。
 */
@Composable
private fun CardParcelRow(parcel: Parcel, now: Long) {
    val hasLocker = parcel.lockerNo != null
    val urgency = urgencyOf(parcel, now)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = parcel.code,
            style = if (hasLocker) ShowCodeText.Code else ShowCodeText.CodeLarge,
            color = Ink,
        )
        if (hasLocker) {
            Spacer(Modifier.width(8.dp))
            Text(text = parcel.lockerNo.orEmpty(), style = ShowCodeText.Caption, color = InkMuted)
        }
        if (urgency != Urgency.SAFE) {
            Spacer(Modifier.weight(1f))
            Text(
                text = urgencyLabel(urgency),
                style = ShowCodeText.Micro,
                color = urgencyColor(urgency),
            )
        }
    }
}

/** 示例卡片：降饱和度 + 斜向水印。必须一眼看出不是真数据，否则用户会以为自己有待取件。 */
@Composable
private fun SamplePointCard(group: PickupPointGroup, now: Long, onClick: () -> Unit) {
    Box {
        // 先画水印再画卡：PointCard 只有描边没有底色，所以压到底下照样看得见，
        // 却不会再糊掉取件码数字——和现场模式那条水印同一个层级理由。
        Text(
            text = stringResource(R.string.sample_badge),
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(-24f),
            style = ShowCodeText.Watermark,
            color = InkWatermark,
        )
        PointCard(group = group, now = now, onClick = onClick, modifier = Modifier.alpha(0.5f))
    }
}

/**
 * 首次启动的空态承担教学职责（edge s2-home.empty-first-time）。
 * 演示入口必须在权限请求之前出现——「先给价值，再要权限」。
 */
@Composable
private fun FirstTimeHeader(onOpenDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_first_time_title),
            style = ShowCodeText.Body,
            color = InkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Ink, ShowCodeShapes.Md)
                .clickable(onClick = onOpenDemo),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_empty_first_time_cta),
                style = ShowCodeText.Section,
                color = OnsiteSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** empty-collection：绿色成功态，不是灰色空态。它消掉的是「我是不是漏了」的焦虑。 */
@Composable
private fun AllDoneCard(monthlyPicked: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencySafeTint, ShowCodeShapes.Md)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = UrgencySafe)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_collection, monthlyPicked),
            style = ShowCodeText.Item,
            color = UrgencySafe,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 最近已取。这一区块的存在理由是「已取」记录的是物理事实，所以中途退出现场模式会保留它，
 * 于是 3 秒撤销窗口一过就没有回头路了——除非首页给一个出口。
 *
 * 默认**折叠成一行**：它是补救出口不是主路径，原来五整行常驻在首页最贵的位置上，
 * 把真正的待取件往下推。窗口缩到 6 小时之后（core 的 `RECENTLY_PICKED_WINDOW_MS`），
 * 再占一整块就更说不通。折叠不持久化——那是「这一次要不要看」，不是一项设置。
 */
@Composable
private fun RecentlyPickedSection(parcels: List<Parcel>, onRestore: (Parcel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val restoreLabel = stringResource(R.string.home_restore)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (open) stringResource(R.string.home_recently_picked_title)
                else stringResource(R.string.home_recently_picked_count, parcels.size),
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
            Text(
                text = stringResource(if (open) R.string.home_collapse else R.string.home_expand),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
        }
        if (!open) return
        Spacer(Modifier.height(6.dp))
        parcels.forEach { parcel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = parcel.code,
                    style = ShowCodeText.CodeSmall,
                    color = InkMuted,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = parcel.pointName,
                    modifier = Modifier.weight(1f),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = restoreLabel,
                    modifier = Modifier
                        .clickable { onRestore(parcel) }
                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }
        }
    }
}

/** 未识别入口行。完整列表与结构化原因住在收容页，首页只负责「让你知道有这件事」。 */
@Composable
private fun UnrecognizedLinkRow(count: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoonTint, ShowCodeShapes.Md)
            .border(1.dp, BorderDueSoon, ShowCodeShapes.Md)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_unrecognized, count),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Item,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.home_unrecognized_action),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
    }
}

/**
 * 反向绑定横幅（story-3 AC5）。只在收到第一个取件点之后出现——
 * 还没有数据时谈「只看某个点」没有意义；可关闭，关闭后不再打扰。
 */
@Composable
private fun WidgetHintBanner(onPin: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_widget_hint),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Meta,
            color = InkSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_widget_hint_action),
            modifier = Modifier
                .clickable(onClick = onPin)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            style = ShowCodeText.Meta,
            color = InkMuted,
        )
    }
}

/**
 * 权限入口。批 1 只有这一个按钮，正式的「价值前置式引导」是 story-1（批 2）。
 * 文案按 brief 的内容设计策略：说清能得到什么、说清数据不出本机，不说「权限不足」。
 */
@Composable
private fun EnableIngestCard(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(text = title, style = ShowCodeText.Item, color = AccentInk)
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = ShowCodeText.Caption, color = InkMuted)
    }
}
