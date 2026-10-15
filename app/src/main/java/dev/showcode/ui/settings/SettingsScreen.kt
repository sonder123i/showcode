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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.HOUR_MS
import dev.showcode.core.HealthIssue
import dev.showcode.core.HealthReport
import dev.showcode.core.Parcel
import dev.showcode.core.Urgency
import dev.showcode.core.UrgencyThresholds
import dev.showcode.core.urgencyOf
import dev.showcode.data.Prefs
import dev.showcode.ui.NAV_BAR_CLEARANCE
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyAtRisk
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import kotlin.math.roundToInt

/**
 * 设置页（edge s9-settings）。两件事住在这里：
 * 1. 两个临期阈值滑块 + 实时预览——不给预览的话用户无法理解 6 小时和 4 小时的差别，
 *    这个设置项等于没有（edge 原话）。顺带让用户自己校准 frame 标注的「默认值是推演的」。
 * 2. 权限体检表——story-5 的完整视图，首页横条只报最严重一项，这里逐项给修复入口。
 */
@Composable
fun SettingsScreen(
    thresholds: UrgencyThresholds,
    parcels: List<Parcel>,
    now: Long,
    report: HealthReport,
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    unrecognizedCount: Int,
    /** 已写下的解析规则条数。0 条时入口仍然要给——不然没人知道有这条路。 */
    ruleCount: Int,
    onOpenRules: () -> Unit,
    /** 推过通知的 App 个数。0 个也要有入口：那一屏的空态本身是诊断信息。 */
    notifyCount: Int,
    onOpenNotify: () -> Unit,
    /** 代取件分组的名字（story-7 AC2 要求用户能改）。 */
    familyLabel: String,
    onFamilyLabel: (String) -> Unit,
    onOpenUnrecognized: () -> Unit,
    onLockerHours: (Int) -> Unit,
    onStationMinutes: (Int) -> Unit,
    onRestoreDefaults: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    var familyLabelDraft by remember { mutableStateOf(familyLabel) }
    val dueSoon = parcels.filter { !it.picked && urgencyOf(it, now, thresholds) == Urgency.DUE_SOON }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ScreenTopBar(title = stringResource(R.string.settings_title))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = NAV_BAR_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_threshold_section)) }
            item {
                ThresholdCard(
                    lockerHours = (thresholds.lockerDueSoonMs / HOUR_MS).toInt(),
                    stationMinutes = thresholds.stationDueSoonMinutes,
                    previewText = if (dueSoon.isEmpty()) {
                        stringResource(R.string.settings_preview_none)
                    } else {
                        stringResource(
                            R.string.settings_preview_some,
                            dueSoon.size,
                            dueSoon.joinToString("、") { it.code },
                        )
                    },
                    previewIsWarning = dueSoon.isNotEmpty(),
                    onLockerHours = onLockerHours,
                    onStationMinutes = onStationMinutes,
                    onRestoreDefaults = onRestoreDefaults,
                )
            }

            /* 分组名可改：写死「帮妈妈取」等于替用户决定他的家庭结构。
               输入框用本地草稿状态，否则清空时会回落到默认值、把正在编辑的那一格弹走。 */
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, Hairline, ShowCodeShapes.Md)
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_family_label),
                        style = ShowCodeText.Item,
                        color = Ink,
                    )
                    Text(
                        text = stringResource(R.string.settings_family_label_hint, familyLabel),
                        style = ShowCodeText.Meta,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = familyLabelDraft,
                        onValueChange = { familyLabelDraft = it; onFamilyLabel(it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        singleLine = true,
                    )
                }
            }

            // 收容页的空态是信任建立瞬间，所以 N=0 时也要有路可达（首页入口只在 N>0 时出现）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Hairline, ShowCodeShapes.Md)
                        .clickable(onClick = onOpenUnrecognized)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.unrecognized_title),
                        modifier = Modifier.weight(1f),
                        style = ShowCodeText.Body,
                        color = Ink,
                    )
                    Text(
                        text = unrecognizedCount.toString(),
                        style = ShowCodeText.Item,
                        color = if (unrecognizedCount > 0) UrgencyDueSoon else InkMuted,
                    )
                }
            }

            /* 解析规则同理，0 条也要出现：这一格是给「知道自己漏过一件、却找不到地方处理」的人的，
               把入口藏在计数后面等于他不漏的时候永远看不见。名字旁边带一句它对谁生效——
               完全离线这件事必须在文案里说出来，不然用户会以为规则要上传。 */
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Hairline, ShowCodeShapes.Md)
                        .clickable(onClick = onOpenRules)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_rules),
                            modifier = Modifier.weight(1f),
                            style = ShowCodeText.Body,
                            color = Ink,
                        )
                        Text(
                            text = stringResource(R.string.settings_rules_count, ruleCount),
                            style = ShowCodeText.Item,
                            color = if (ruleCount > 0) Ink else InkMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_rules_hint),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
            }

            /* 通知来源同样 0 个也要出现：那一屏的空态本身就是诊断——它说的是
               「监听还没见过任何通知」，而这正是权限没给或被 ROM 杀掉时唯一的可见线索。 */
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Hairline, ShowCodeShapes.Md)
                        .clickable(onClick = onOpenNotify)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_notify),
                            modifier = Modifier.weight(1f),
                            style = ShowCodeText.Body,
                            color = Ink,
                        )
                        Text(
                            text = stringResource(R.string.settings_notify_count, notifyCount),
                            style = ShowCodeText.Item,
                            color = if (notifyCount > 0) Ink else InkMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_notify_hint),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_health_section)) }
            item {
                HealthTable(
                    report = report,
                    smsGranted = smsGranted,
                    notificationEnabled = notificationEnabled,
                    onRequestSms = onRequestSms,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAppDetails = onOpenAppDetails,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = ShowCodeText.Action,
        color = InkMuted,
    )
}

@Composable
private fun ThresholdCard(
    lockerHours: Int,
    stationMinutes: Int,
    previewText: String,
    previewIsWarning: Boolean,
    onLockerHours: (Int) -> Unit,
    onStationMinutes: (Int) -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(16.dp)
    ) {
        DiscreteSlider(
            label = stringResource(R.string.settings_locker_label, lockerHours),
            options = Prefs.LOCKER_OPTIONS,
            current = lockerHours,
            onSelect = onLockerHours,
        )
        Spacer(Modifier.height(14.dp))
        DiscreteSlider(
            label = stringResource(R.string.settings_station_label, stationMinutes),
            options = Prefs.STATION_OPTIONS,
            current = stationMinutes,
            onSelect = onStationMinutes,
        )

        Spacer(Modifier.height(10.dp))
        // 实时预览：阈值一改，这一行立刻告诉你哪几件跨进了临期
        Text(
            text = previewText,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, ShowCodeShapes.Md)
                .padding(12.dp),
            style = ShowCodeText.Meta,
            color = if (previewIsWarning) UrgencyDueSoon else InkMuted,
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_restore),
            modifier = Modifier
                .clickable(onClick = onRestoreDefaults)
                .padding(vertical = 4.dp),
            style = ShowCodeText.Action,
            color = Accent,
        )
    }
}

/** 档位是离散集合（frame 锁定 2/4/6/8 与 30/60/90/120），所以滑块只有 4 个停靠点。 */
@Composable
private fun DiscreteSlider(label: String, options: List<Int>, current: Int, onSelect: (Int) -> Unit) {
    val index = options.indexOf(current).coerceAtLeast(0)
    Text(text = label, style = ShowCodeText.Item, color = Ink)
    Slider(
        value = index.toFloat(),
        onValueChange = { onSelect(options[it.roundToInt().coerceIn(0, options.lastIndex)]) },
        valueRange = 0f..(options.lastIndex).toFloat(),
        steps = options.lastIndex - 1,
    )
}

@Composable
private fun HealthTable(
    report: HealthReport,
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    onRequestSms: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report.allGood) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencySafe)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_health_all_good),
                    style = ShowCodeText.Item,
                    color = UrgencySafe,
                )
            }
        }

        HealthRow(
            title = stringResource(R.string.health_sms),
            ok = smsGranted,
            statusText = stringResource(if (smsGranted) R.string.health_on else R.string.health_off),
            fixLabel = stringResource(R.string.health_fix_sms),
            onFix = onRequestSms,
            showFix = !smsGranted,
        )
        HealthRow(
            title = stringResource(R.string.health_notification),
            ok = notificationEnabled,
            statusText = stringResource(if (notificationEnabled) R.string.health_on else R.string.health_off),
            fixLabel = stringResource(R.string.health_fix_notification),
            onFix = onOpenNotificationSettings,
            showFix = !notificationEnabled,
        )
        if (HealthIssue.BACKGROUND_SUSPECTED in report.issues) {
            HealthRow(
                title = stringResource(R.string.health_background),
                ok = false,
                statusText = stringResource(R.string.health_off),
                hint = stringResource(R.string.health_background_hint),
                fixLabel = stringResource(R.string.health_fix_background),
                onFix = onOpenAppDetails,
                showFix = true,
                statusColor = UrgencyDueSoon,
            )
        }
        if (HealthIssue.MIUI_SMS_CLASS in report.manualChecks) {
            HealthRow(
                title = stringResource(R.string.health_miui),
                ok = false,
                statusText = stringResource(R.string.health_unknown),
                hint = stringResource(R.string.health_miui_hint),
                fixLabel = stringResource(R.string.health_fix_miui),
                onFix = onOpenAppDetails,
                showFix = true,
                statusColor = UrgencyDueSoon,
            )
        }
    }
}

@Composable
private fun HealthRow(
    title: String,
    ok: Boolean,
    statusText: String,
    hint: String? = null,
    fixLabel: String,
    onFix: () -> Unit,
    showFix: Boolean,
    statusColor: Color? = null,
) {
    val tint = statusColor ?: if (ok) UrgencySafe else UrgencyAtRisk
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Body,
                color = Ink,
            )
            Text(
                text = statusText,
                modifier = Modifier
                    .background(color = tint.copy(alpha = 0.12f), shape = ShowCodeShapes.Sm)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = ShowCodeText.Micro,
                color = tint,
            )
            if (showFix) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = fixLabel,
                    modifier = Modifier
                        .clickable(onClick = onFix)
                        .padding(vertical = 4.dp),
                    style = ShowCodeText.Action,
                    color = Accent,
                )
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = hint, style = ShowCodeText.Caption, color = InkMuted)
        }
    }
}
