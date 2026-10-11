package dev.showcode.ui.inbox

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.Source
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.PickupCodeParser
import dev.showcode.core.parse.UnrecognizedReason
import dev.showcode.data.UnrecognizedMessage
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.SurfaceInset
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe

/**
 * 未识别收容页（edge s7-unrecognized）。
 * 空态是信任建立的瞬间——用户知道「如果有问题我会被告知」，
 * 所以没有内容时这一页仍然要站得住，不能因为空就藏掉入口。
 */
@Composable
fun UnrecognizedScreen(
    messages: List<UnrecognizedMessage>,
    now: Long,
    onBack: () -> Unit,
    onExtract: (UnrecognizedMessage) -> Unit,
    onDismiss: (UnrecognizedMessage) -> Unit,
) {
    val noSender = stringResource(R.string.unrecognized_no_sender)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ScreenTopBar(title = stringResource(R.string.unrecognized_title), onBack = onBack)

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = UrgencySafe,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.unrecognized_empty),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnsiteInk,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.unrecognized_empty_hint),
                    fontSize = 13.sp,
                    color = OnsiteInk.copy(alpha = 0.55f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.home_unrecognized_hint),
                        fontSize = 12.sp,
                        color = OnsiteInk.copy(alpha = 0.6f),
                    )
                }
                items(messages, key = { it.id }) { message ->
                    UnrecognizedRow(
                        message = message,
                        now = now,
                        fallbackSender = noSender,
                        onExtract = { onExtract(message) },
                        onDismiss = { onDismiss(message) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnrecognizedRow(
    message: UnrecognizedMessage,
    now: Long,
    fallbackSender: String,
    onExtract: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.sender.ifBlank { fallbackSender },
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OnsiteInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTimeText(message.receivedAt, now),
                fontSize = 11.sp,
                color = OnsiteInk.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = message.body, fontSize = 13.sp, color = OnsiteInk.copy(alpha = 0.75f))
        Spacer(Modifier.height(6.dp))
        Text(
            text = unrecognizedReasonText(message.reason),
            fontSize = 12.sp,
            color = UrgencyDueSoon,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.unrecognized_extract),
                modifier = Modifier.clickable(onClick = onExtract),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
            Text(
                text = stringResource(R.string.unrecognized_ignore),
                modifier = Modifier.clickable(onClick = onDismiss),
                fontSize = 13.sp,
                color = OnsiteInk.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun unrecognizedReasonText(reason: UnrecognizedReason): String = stringResource(
    when (reason) {
        UnrecognizedReason.CODE_LIVES_IN_APP -> R.string.unrecognized_code_in_app
        UnrecognizedReason.NO_SENDER_NUMBER -> R.string.unrecognized_no_sender_number
        UnrecognizedReason.NO_CODE_FOUND -> R.string.unrecognized_no_code
    }
)

/**
 * 手动导入 / 手动提取。拒绝短信权限之后这是一等公民退路，不是藏起来的备用功能
 * （edge s1 permission-sms-denied：不给一句「需要权限」，给一条能走通的路）。
 */
@Composable
fun ManualImportScreen(
    prefillRaw: String,
    onBack: () -> Unit,
    onSave: (code: String, pointName: String, lockerNo: String?, raw: String) -> Unit,
) {
    var raw by remember(prefillRaw) { mutableStateOf(prefillRaw) }
    var code by remember { mutableStateOf("") }
    var pointName by remember { mutableStateOf("") }
    var lockerNo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ScreenTopBar(title = stringResource(R.string.manual_import_title), onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_import_raw)) },
                    minLines = 3,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.manual_import_auto),
                    modifier = Modifier.clickable {
                        // 原文保留（edge s6-manual-import 红线）：自动提取只填字段，不清空原文
                        val outcome = PickupCodeParser.parse(
                            IncomingMessage(
                                sender = "",
                                body = raw,
                                receivedAt = System.currentTimeMillis(),
                                channel = Source.MANUAL,
                            )
                        )
                        when (outcome) {
                            is ParseOutcome.Parsed -> {
                                code = outcome.parcel.code
                                pointName = outcome.parcel.pointName
                                lockerNo = outcome.parcel.lockerNo.orEmpty()
                                error = null
                            }
                            is ParseOutcome.Rejected ->
                                error = context.getString(R.string.manual_import_auto_failed)
                            ParseOutcome.Ignored ->
                                error = context.getString(R.string.manual_import_auto_failed)
                        }
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                )
            }
            item {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_import_code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            item {
                OutlinedTextField(
                    value = pointName,
                    onValueChange = { pointName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_import_point)) },
                )
            }
            item {
                OutlinedTextField(
                    value = lockerNo,
                    onValueChange = { lockerNo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_import_locker)) },
                )
            }
            item {
                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceInset, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        fontSize = 13.sp,
                        color = UrgencyDueSoon,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(OnsiteInk, RoundedCornerShape(14.dp))
                        .clickable {
                            val trimmedCode = code.trim()
                            val trimmedName = pointName.trim()
                            if (trimmedCode.isEmpty() || trimmedName.isEmpty()) {
                                error = context.getString(R.string.manual_import_need_both)
                            } else {
                                onSave(trimmedCode, trimmedName, lockerNo.trim().takeIf { it.isNotEmpty() }, raw)
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.manual_import_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnsiteSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScreenTopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OnsiteInk,
        )
    }
}
