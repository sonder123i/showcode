package dev.showcode.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.PickupCodeParser

/**
 * 查快递主页：粘贴快递单号 → 自动识别 → 跳转到对应取件码或手动确认。
 *
 * 这是用户从「剪贴板里复制了单号」回到 App 的第一站：不需要再翻短信/通知，
 * 直接在这里填一次，之后所有查过的单号都留在这张表里供快速复用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupTabScreen(
    prefilledText: String,
    knownBodies: Set<String>,
    history: List<String>,
    onBack: () -> Unit,
    onSave: (code: String, pointName: String, lockerNo: String?, raw: String) -> Boolean,
) {
    val context = LocalContext.current
    val repository = context.app.repository
    var text by remember { mutableStateOf(prefilledText) }
    var extractedCode by remember { mutableStateOf<String?>(null) }
    var extractedPoint by remember { mutableStateOf<String?>(null) }
    var extractedLocker by remember { mutableStateOf<String?>(null) }
    var saveAttempted by remember { mutableStateOf(false) }

    // 进入页面时自动尝试解析剪贴板内容
    LaunchedEffect(prefilledText) {
        if (prefilledText.isNotBlank()) {
            tryParseAndExtract(prefilledText, repository.enabledRules())
                ?.let { (code, point, locker) ->
                    extractedCode = code
                    extractedPoint = point
                    extractedLocker = locker
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        /* 顶栏：返回键 + 提示语。不画搜索框——这里只处理剪贴板这一件事，搜是首页的事。 */
        TopAppBar(
            title = { Text(stringResource(R.string.lookup_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.onsite_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                titleContentColor = androidx.compose.ui.graphics.Color.Black,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        /* 输入框：如果剪贴板有文本，进来就自动填；没有就留空让用户手输。 */
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.lookup_field_text)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )

        /* 自动提取结果区：认出来就列出来，没认出来就留白等用户手动填。 */
        if (extractedCode != null || extractedPoint != null || extractedLocker != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${stringResource(R.string.lookup_auto_extract)}:", fontWeight = FontWeight.Medium)
                    extractedCode?.let { code ->
                        Text(text = "取件码：$code", color = androidx.compose.ui.graphics.Color.Green)
                    }
                    extractedPoint?.let { point ->
                        Text(text = "取件点：$point")
                    }
                    extractedLocker?.let { locker ->
                        Text(text = "柜号：$locker")
                    }
                }
                /* 一键填充按钮：认出来的字段自动补到下面那三个表单里。 */
                IconButton(onClick = {
                    extractedCode?.let { text += "\n$it" }
                    extractedPoint?.let { text += "\n$it" }
                    extractedLocker?.let { text += "\n$it" }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "一键填充")
                }
            }
        } else {
            /* 没认出来就告诉用户哪里有问题，或者干脆留白让他自己判断。 */
            if (text.isNotBlank()) {
                Text(
                    text = stringResource(R.string.lookup_auto_extract_failed),
                    color = androidx.compose.ui.graphics.Color.Red,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        /* 手动补全表单：自动提取救不了的地方，用户必须亲手填。 */
        OutlinedTextField(
            value = extractedCode ?: "",
            onValueChange = { /* no-op for manual input */ },
            enabled = false,
            label = { Text(stringResource(R.string.lookup_manual_code)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = extractedPoint ?: "",
            onValueChange = { /* no-op for manual input */ },
            enabled = false,
            label = { Text(stringResource(R.string.lookup_manual_point)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = extractedLocker ?: "",
            onValueChange = { /* no-op for manual input */ },
            enabled = false,
            label = { Text(stringResource(R.string.manual_import_locker)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(24.dp))

        /* 保存按钮：失败时留在原地，成功就清空表单回首页。 */
        androidx.compose.material3.Button(
            onClick = {
                val success = if (extractedCode != null && extractedPoint != null) {
                    onSave(extractedCode!!, extractedPoint!!, extractedLocker, text)
                } else {
                    false
                }
                saveAttempted = true
                if (success) {
                    text = ""
                    extractedCode = null
                    extractedPoint = null
                    extractedLocker = null
                }
            },
            enabled = extractedCode != null && extractedPoint != null && !saveAttempted,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.lookup_save))
        }

        if (saveAttempted && !extractedCode!!.isNullOrBlank() && !extractedPoint!!.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.lookup_save_failed),
                color = androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        /* 历史记录栏：最近查过的单号，点击即可复用。 */
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.lookup_history),
            fontWeight = FontWeight.Bold,
        )
        if (history.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                history.take(5).forEach { item ->
                    androidx.compose.material3.Card(
                        onClick = { text = item },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 尝试从文本中提取取件码、取件点和柜号。
 * 使用内置解析器，返回结构化结果或 null（解析失败）。
 */
private fun tryParseAndExtract(text: String, rules: List<dev.showcode.core.ParseRule>): Pair<String, String, String?>? {
    val message = IncomingMessage(
        sender = "",
        body = text,
        receivedAt = System.currentTimeMillis(),
        channel = dev.showcode.core.Source.SMS,
    )
    
    return when (val outcome = PickupCodeParser.parse(message, rules)) {
        is dev.showcode.core.parse.ParseOutcome.Parsed -> {
            val parcel = outcome.parcels.firstOrNull() ?: return null
            // 至少要有码和点才能算成功
            if (parcel.code.isNotBlank() && parcel.pointName.isNotBlank()) {
                Pair(parcel.code, parcel.pointName, parcel.lockerNo)
            } else {
                null
            }
        }
        else -> null
    }
}
