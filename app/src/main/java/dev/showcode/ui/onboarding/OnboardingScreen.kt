package dev.showcode.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.HealthReport
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencyDueSoonTint
import dev.showcode.ui.theme.UrgencySafe

enum class OnboardStep { WELCOME, SMS, NOTIFICATION, WIDGET, READY }

private const val TOTAL_STEPS = 4

/**
 * 首次启动引导（story-1）。核心决策只有一条：**先理解价值，再请求权限**。
 * 这里的「理解价值」不用问卷、不用动画，用现场模式演示——
 * 价值就在那一屏超大取件码里。所以 WELCOME 的主 CTA 是看演示，不是要权限。
 *
 * 每一步都可跳过，拒绝不锁死任何功能（brief 的用户引导策略）。
 */
@Composable
fun OnboardingScreen(
    step: OnboardStep,
    report: HealthReport,
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    widgetAdded: Boolean,
    smsAttempted: Boolean,
    onStep: (OnboardStep) -> Unit,
    onEnterDemo: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPinWidget: () -> Unit,
    onManualImport: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when (step) {
            OnboardStep.WELCOME -> Welcome(onEnterDemo = onEnterDemo, onSetup = { onStep(OnboardStep.SMS) })
            OnboardStep.SMS -> SmsStep(
                denied = smsAttempted && !smsGranted,
                onAllow = onRequestSms,
                onDecline = { onStep(OnboardStep.NOTIFICATION) },
                onSkip = { onStep(OnboardStep.NOTIFICATION) },
            )
            OnboardStep.NOTIFICATION -> NotificationStep(
                onEnable = onOpenNotificationSettings,
                onNext = { onStep(OnboardStep.WIDGET) },
                onSkip = { onStep(OnboardStep.WIDGET) },
            )
            OnboardStep.WIDGET -> WidgetStep(
                onDone = { onPinWidget(); onStep(OnboardStep.READY) },
                onSkip = { onStep(OnboardStep.READY) },
            )
            OnboardStep.READY -> ReadyStep(
                report = report,
                smsGranted = smsGranted,
                notificationEnabled = notificationEnabled,
                widgetAdded = widgetAdded,
                onFinish = onFinish,
                onManualImport = onManualImport,
            )
        }
    }
}

@Composable
private fun Welcome(onEnterDemo: () -> Unit, onSetup: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
        ) {
            Text(text = "📦", style = ShowCodeText.Display)
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.app_name), style = ShowCodeText.Count, color = Ink)
            Spacer(Modifier.height(10.dp))
            Text(text = stringResource(R.string.ob_welcome_sub), style = ShowCodeText.Meta, color = InkSecondary)
            Spacer(Modifier.height(18.dp))
            ValueRow(icon = "📩", text = stringResource(R.string.ob_value_1))
            ValueRow(icon = "📍", text = stringResource(R.string.ob_value_2))
            ValueRow(icon = "⏰", text = stringResource(R.string.ob_value_3))
            /* 隐私承诺必须整张卡都在折叠线之上：在 640dp 高的屏上原来被主 CTA
               切掉最后一行，而「只有公共数据会下载、不上传」正是这半句最要紧。
               宁可让上面三段各少 10dp，也不能让它需要滚动才看得全。 */
            Spacer(Modifier.height(16.dp))
            PrivacyPromise()
        }
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
            // 主 CTA 是先看演示，不是先要权限
            PrimaryButton(text = stringResource(R.string.ob_cta_demo), onClick = onEnterDemo)
            Spacer(Modifier.height(10.dp))
            GhostButton(text = stringResource(R.string.ob_cta_setup), onClick = onSetup)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ob_footer),
                modifier = Modifier.fillMaxWidth(),
                style = ShowCodeText.Micro,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ValueRow(icon: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = icon, style = ShowCodeText.Headline, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = ShowCodeText.Body, color = Ink)
    }
}

/** 隐私承诺块。放在每一次权限请求的上方——先安心，再要权限。 */
@Composable
private fun PrivacyPromise() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(14.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = UrgencySafe)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = stringResource(R.string.ob_privacy_title), style = ShowCodeText.Action, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(text = stringResource(R.string.ob_privacy_body), style = ShowCodeText.Caption, color = InkSecondary)
        }
    }
}

@Composable
private fun StepBar(step: OnboardStep, onSkip: () -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ob_step, step.ordinal, TOTAL_STEPS),
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
            Text(
                text = stringResource(R.string.ob_skip),
                modifier = Modifier.clickable(onClick = onSkip),
                style = ShowCodeText.Caption,
                color = Accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(SurfaceCard, ShowCodeShapes.Pill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = step.ordinal.toFloat() / TOTAL_STEPS)
                    .height(4.dp)
                    .background(Ink, ShowCodeShapes.Pill)
            )
        }
    }
}

@Composable
private fun SmsStep(denied: Boolean, onAllow: () -> Unit, onDecline: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        StepBar(step = OnboardStep.SMS, onSkip = onSkip)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
        ) {
            Text(text = stringResource(R.string.ob_sms_title), style = ShowCodeText.Headline, color = Ink)
            Spacer(Modifier.height(10.dp))
            Text(text = stringResource(R.string.ob_sms_body), style = ShowCodeText.Body, color = InkSecondary)
            Spacer(Modifier.height(20.dp))
            // 价值对比：不是「需要权限」，而是「有它 / 没它」的差别
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareCard(
                    title = stringResource(R.string.ob_sms_allow_title),
                    desc = stringResource(R.string.ob_sms_allow_desc),
                    highlighted = true,
                    modifier = Modifier.weight(1f),
                )
                CompareCard(
                    title = stringResource(R.string.ob_sms_deny_title),
                    desc = stringResource(R.string.ob_sms_deny_desc),
                    highlighted = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            PrivacyPromise()
            if (denied) {
                Spacer(Modifier.height(16.dp))
                InfoAlert(text = stringResource(R.string.ob_sms_denied))
            }
        }
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 8.dp)) {
            PrimaryButton(text = stringResource(R.string.ob_sms_allow_cta), onClick = onAllow)
            Spacer(Modifier.height(8.dp))
            GhostButton(text = stringResource(R.string.ob_sms_deny_cta), onClick = onDecline)
        }
    }
}

@Composable
private fun CompareCard(title: String, desc: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) Accent else Hairline,
                shape = ShowCodeShapes.Md,
            )
            .background(if (highlighted) AccentTint else SurfaceCard, ShowCodeShapes.Md)
            .padding(12.dp)
    ) {
        Text(text = title, style = ShowCodeText.Caption, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        Text(text = desc, style = ShowCodeText.Item, color = Ink)
    }
}

@Composable
private fun NotificationStep(onEnable: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        StepBar(step = OnboardStep.NOTIFICATION, onSkip = onSkip)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
        ) {
            Text(text = stringResource(R.string.ob_notif_title), style = ShowCodeText.Headline, color = Ink)
            Spacer(Modifier.height(10.dp))
            Text(text = stringResource(R.string.ob_notif_body), style = ShowCodeText.Body, color = InkSecondary)
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.ob_notif_list_header), style = ShowCodeText.Caption, color = InkMuted)
            Spacer(Modifier.height(8.dp))
            SourceRow(stringResource(R.string.ob_notif_src_1))
            SourceRow(stringResource(R.string.ob_notif_src_2))
            SourceRow(stringResource(R.string.ob_notif_src_3))
            SourceRow(stringResource(R.string.ob_notif_src_4))
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.ob_notif_note), style = ShowCodeText.Caption, color = InkMuted)
            Spacer(Modifier.height(16.dp))
            PrivacyPromise()
        }
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 8.dp)) {
            PrimaryButton(text = stringResource(R.string.ob_notif_cta), onClick = { onEnable(); onNext() })
            Spacer(Modifier.height(8.dp))
            GhostButton(text = stringResource(R.string.ob_notif_skip), onClick = onNext)
        }
    }
}

@Composable
private fun SourceRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = ShowCodeText.Meta, color = Ink)
    }
}

@Composable
private fun WidgetStep(onDone: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        StepBar(step = OnboardStep.WIDGET, onSkip = onSkip)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
        ) {
            Text(text = stringResource(R.string.ob_widget_title), style = ShowCodeText.Headline, color = Ink)
            Spacer(Modifier.height(10.dp))
            Text(text = stringResource(R.string.ob_widget_body), style = ShowCodeText.Body, color = InkSecondary)
            Spacer(Modifier.height(18.dp))
            NumberedStep(number = 1, text = stringResource(R.string.ob_widget_step_1))
            NumberedStep(number = 2, text = stringResource(R.string.ob_widget_step_2))
            NumberedStep(number = 3, text = stringResource(R.string.ob_widget_step_3))
            Spacer(Modifier.height(8.dp))
            InfoAlert(text = stringResource(R.string.ob_widget_caveat))
        }
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 8.dp)) {
            PrimaryButton(text = stringResource(R.string.ob_widget_done), onClick = onDone)
            Spacer(Modifier.height(8.dp))
            GhostButton(text = stringResource(R.string.ob_widget_skip), onClick = onSkip)
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Accent, ShowCodeShapes.Md),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = ShowCodeText.Action, color = OnsiteSurface)
        }
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = ShowCodeText.Body, color = Ink)
    }
}

@Composable
private fun ReadyStep(
    report: HealthReport,
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    widgetAdded: Boolean,
    onFinish: () -> Unit,
    onManualImport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StepBar(step = OnboardStep.READY, onSkip = onFinish)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(44.dp), tint = UrgencySafe)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.ob_ready_title), style = ShowCodeText.Headline, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.ob_ready_body),
                    style = ShowCodeText.Body,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
            if (report.issues.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                InfoAlert(text = stringResource(R.string.ob_ready_partial, report.issues.size))
            }
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.ob_ready_health), style = ShowCodeText.Caption, color = InkMuted)
            Spacer(Modifier.height(8.dp))
            CheckRow(title = stringResource(R.string.health_sms), ok = smsGranted)
            CheckRow(title = stringResource(R.string.health_notification), ok = notificationEnabled)
            CheckRow(title = stringResource(R.string.ob_widget_title), ok = widgetAdded)
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.ob_ready_note), style = ShowCodeText.Micro, color = InkMuted)
        }
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 8.dp)) {
            PrimaryButton(text = stringResource(R.string.ob_ready_cta), onClick = onFinish)
            Spacer(Modifier.height(8.dp))
            GhostButton(text = stringResource(R.string.ob_ready_manual), onClick = onManualImport)
        }
    }
}

@Composable
private fun CheckRow(title: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = ShowCodeText.Body, color = Ink)
        Text(
            text = stringResource(if (ok) R.string.health_on else R.string.health_off),
            modifier = Modifier
                .background((if (ok) UrgencySafe else InkMuted).copy(alpha = 0.12f), ShowCodeShapes.Sm)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = ShowCodeText.Micro,
            color = if (ok) UrgencySafe else InkMuted,
        )
    }
}

@Composable
private fun InfoAlert(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UrgencyDueSoonTint, ShowCodeShapes.Md)
            .background(UrgencyDueSoonTint, ShowCodeShapes.Md)
            .border(1.dp, UrgencyDueSoon, ShowCodeShapes.Md)
            .padding(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = UrgencyDueSoon)
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = ShowCodeText.Meta, color = Ink)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Ink, ShowCodeShapes.Md)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ShowCodeText.Section, color = OnsiteSurface)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ShowCodeText.Body, color = InkSecondary)
    }
}
