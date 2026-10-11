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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.SurfaceInset
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
    onOpenUnrecognized: () -> Unit,
    onLockerHours: (Int) -> Unit,
    onStationMinutes: (Int) -> Unit,
    onRestoreDefaults: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onBack: () -> Unit,
) {
    val dueSoon = parcels.filter { !it.picked && urgencyOf(it, now, thresholds) == Urgency.DUE_SOON }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars)
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
                text = stringResource(R.string.settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnsiteInk,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
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

            // 收容页的空态是信任建立瞬间，所以 N=0 时也要有路可达（首页入口只在 N>0 时出现）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenUnrecognized)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.unrecognized_title),
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = OnsiteInk,
                    )
                    Text(
                        text = unrecognizedCount.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (unrecognizedCount > 0) UrgencyDueSoon else OnsiteInk.copy(alpha = 0.4f),
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
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = OnsiteInk.copy(alpha = 0.5f),
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
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
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
                .background(SurfaceInset, RoundedCornerShape(10.dp))
                .padding(12.dp),
            fontSize = 13.sp,
            color = if (previewIsWarning) UrgencyDueSoon else OnsiteInk.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_restore),
            modifier = Modifier
                .clickable(onClick = onRestoreDefaults)
                .padding(vertical = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
        )
    }
}

/** 档位是离散集合（frame 锁定 2/4/6/8 与 30/60/90/120），所以滑块只有 4 个停靠点。 */
@Composable
private fun DiscreteSlider(label: String, options: List<Int>, current: Int, onSelect: (Int) -> Unit) {
    val index = options.indexOf(current).coerceAtLeast(0)
    Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnsiteInk)
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
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report.allGood) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencySafe)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_health_all_good),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
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
                fontSize = 14.sp,
                color = OnsiteInk,
            )
            Text(
                text = statusText,
                modifier = Modifier
                    .background(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            if (showFix) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = fixLabel,
                    modifier = Modifier
                        .clickable(onClick = onFix)
                        .padding(vertical = 4.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                )
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = hint, fontSize = 12.sp, color = OnsiteInk.copy(alpha = 0.6f))
        }
    }
}
