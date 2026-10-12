package dev.showcode.ui.home

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import dev.showcode.core.Urgency
import dev.showcode.core.urgencyOf
import dev.showcode.ui.closingLabel
import dev.showcode.ui.pointKindLabel
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.points.MergeUndoBar
import dev.showcode.ui.points.SuspectHintCard
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.SurfaceInset
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencyAtRisk
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.urgencyColor
import dev.showcode.ui.urgencyLabel

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
    /* 陈旧提示对任何一条收录通道都成立。以前它只被短信权限门住，
       于是「拒了短信、只开通知监听」这个完全合法的配置下，用户永远看不到「可能不是最新」。 */
    val isStale = (state.smsPermissionGranted || state.notificationAccessGranted) &&
        (state.lastSyncAt == null || now - state.lastSyncAt > STALE_THRESHOLD_MS)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
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
                Text(
                    text = stringResource(R.string.home_title),
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnsiteInk,
                )
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = OnsiteInk.copy(alpha = 0.7f),
                    )
                }
            }

            // permission-health-bar：跨屏常驻、不可被滚动隐藏——它挂在 LazyColumn 外面。
            // 权限失效是静默的，用户不会收到系统通知，只会觉得「最近好像没新快递」。
            HealthBar(report = report, now = now, lastSyncAt = state.lastSyncAt, onFix = onFixWorst, onView = onOpenSettings)

            // error-stale-data：不会被滚动隐藏——它挂在 LazyColumn 外面
            if (isStale) {
                StaleBar(lastSyncAt = state.lastSyncAt, now = now, onRefresh = onRefresh)
            }

            /* 合并后的 5 秒撤销（AC2 的「立刻有一个明显的撤销入口」）。
               和权限条一样住在 LazyColumn 外面：一个能被滚走的反悔入口等于没有反悔。 */
            mergeUndoName?.let { MergeUndoBar(keptName = it, onUndo = onUndoMerge) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = REFRESH_PILL_CLEARANCE),
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
                            fontSize = 12.sp,
                            color = OnsiteInk.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(state.groups, key = { it.key }) { group ->
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
                    if (state.empty == HomeEmpty.ALL_DONE) {
                        item { AllDoneCard(monthlyPicked = state.monthlyPicked) }
                    }

                    // 误触「已取」超过 3 秒撤销窗口之后的唯一补救出口。
                    // 设计里点名它落在「首页 / 历史」，批 1 还没有历史页，所以落在首页。
                    if (state.recentlyPicked.isNotEmpty()) {
                        item { RecentlyPickedSection(parcels = state.recentlyPicked, onRestore = onRestoreParcel) }
                    }
                }

                // 解析失败必须显性化（story-2 的 risk）：首页给入口行，完整列表在收容页。
                if (state.unrecognizedCount > 0) {
                    item { UnrecognizedLinkRow(count = state.unrecognizedCount, onOpen = onOpenUnrecognized) }
                }

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

                // 手动导入是「拒绝短信权限也能用」的一等退路（edge s1 permission-sms-denied），
                // 引导结束之后也必须可达，所以常驻在列表末尾。
                item {
                    Text(
                        text = stringResource(R.string.ob_ready_manual),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManualImport)
                            .padding(vertical = 10.dp),
                        fontSize = 14.sp,
                        color = OnsiteInk.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 浮层落点：胶囊常驻底部，卡片从它下面滚过去。
        // 未加载完不出现——那时报「从未同步」是在制造焦虑而不是在提供出口。
        if (state.loaded) {
            RefreshPill(
                lastSyncAt = state.lastSyncAt,
                now = now,
                onRefresh = onRefresh,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
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
            .background(UrgencyAtRisk.copy(alpha = 0.10f))
            .clickable(onClick = onView)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyAtRisk)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnsiteInk)
            if (report.restCount > 0) {
                Text(
                    text = stringResource(R.string.health_bar_more, report.restCount),
                    fontSize = 11.sp,
                    color = OnsiteInk.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.health_bar_view),
            modifier = Modifier
                .clickable(onClick = onFix)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = UrgencyAtRisk,
        )
    }
}

@Composable
private fun StaleBar(lastSyncAt: Long?, now: Long, onRefresh: () -> Unit) {
    val neverSynced = stringResource(R.string.home_never_synced)
    val whenText = lastSyncAt?.let { relativeTimeText(it, now) } ?: neverSynced
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoon.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_stale, whenText),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = OnsiteInk,
        )
        Text(
            text = stringResource(R.string.refresh_action),
            modifier = Modifier
                .clickable(onClick = onRefresh)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OnsiteInk,
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
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // 色条：首页唯一的颜色编码位置。颜色之外永远有文字冗余（brief 的人群适配策略）
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(40.dp)
                .background(urgencyColor(group.worst), RoundedCornerShape(5.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // boundary-long-text：首页截断，现场模式完整显示——这是两个模式的真实分歧
                Text(
                    text = group.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnsiteInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = group.items.size.toString(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnsiteInk,
                    style = TabularNumbers,
                )
                Text(
                    text = stringResource(R.string.home_count_unit),
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    fontSize = 12.sp,
                    color = OnsiteInk.copy(alpha = 0.5f),
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
                            tint = OnsiteInk.copy(alpha = 0.45f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (group.worst == Urgency.SAFE) OnsiteInk.copy(alpha = 0.6f) else urgencyColor(group.worst),
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
                        fontSize = 12.sp,
                        color = OnsiteInk.copy(alpha = 0.45f),
                        style = TabularNumbers,
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
            fontSize = if (hasLocker) 17.sp else 19.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = TabularNumbers,
            color = OnsiteInk,
        )
        if (hasLocker) {
            Spacer(Modifier.width(8.dp))
            Text(text = parcel.lockerNo.orEmpty(), fontSize = 12.sp, color = OnsiteInk.copy(alpha = 0.5f))
        }
        if (urgency != Urgency.SAFE) {
            Spacer(Modifier.weight(1f))
            Text(
                text = urgencyLabel(urgency),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = urgencyColor(urgency),
            )
        }
    }
}

/** 示例卡片：降饱和度 + 斜向水印。必须一眼看出不是真数据，否则用户会以为自己有待取件。 */
@Composable
private fun SamplePointCard(group: PickupPointGroup, now: Long, onClick: () -> Unit) {
    Box {
        PointCard(group = group, now = now, onClick = onClick, modifier = Modifier.alpha(0.5f))
        Text(
            text = stringResource(R.string.sample_badge),
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(-24f),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black.copy(alpha = 0.08f),
            letterSpacing = 0.15.em,
        )
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
            fontSize = 15.sp,
            color = OnsiteInk.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(OnsiteInk, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenDemo),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_empty_first_time_cta),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
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
            .background(UrgencySafe.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = UrgencySafe)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_collection, monthlyPicked),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = UrgencySafe,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 最近已取。这一区块的存在理由是「已取」记录的是物理事实，所以中途退出现场模式会保留它，
 * 于是 3 秒撤销窗口一过就没有回头路了——除非首页给一个出口。
 */
@Composable
private fun RecentlyPickedSection(parcels: List<Parcel>, onRestore: (Parcel) -> Unit) {
    val restoreLabel = stringResource(R.string.home_restore)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.home_recently_picked_title),
            fontSize = 12.sp,
            color = OnsiteInk.copy(alpha = 0.55f),
        )
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
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = TabularNumbers,
                    color = OnsiteInk.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = parcel.pointName,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = OnsiteInk.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = restoreLabel,
                    modifier = Modifier
                        .clickable { onRestore(parcel) }
                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
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
            .border(1.dp, UrgencyDueSoon.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_unrecognized, count),
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = OnsiteInk,
        )
        Text(
            text = stringResource(R.string.home_unrecognized_action),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
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
            .background(SurfaceInset, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_widget_hint),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = OnsiteInk.copy(alpha = 0.75f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_widget_hint_action),
            modifier = Modifier
                .clickable(onClick = onPin)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
        )
        Text(
            text = "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            fontSize = 13.sp,
            color = OnsiteInk.copy(alpha = 0.4f),
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
            .background(SurfaceInset, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Accent)
        Spacer(Modifier.height(4.dp))
        Text(text = body, fontSize = 12.sp, color = OnsiteInk.copy(alpha = 0.6f))
    }
}
