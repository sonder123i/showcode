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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
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
import dev.showcode.core.ClipboardIntake
import dev.showcode.core.ClipboardVerdict
import dev.showcode.core.Owner
import dev.showcode.core.Source
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.MessageDomain
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.PickupCodeParser
import dev.showcode.core.parse.UnrecognizedReason
import dev.showcode.data.UnrecognizedMessage
import dev.showcode.ui.readText
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.Canvas
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import kotlinx.coroutines.launch

/**
 * 未识别收容页（edge s7-unrecognized）。
 * 空态是信任建立的瞬间——用户知道「如果有问题我会被告知」，
 * 所以没有内容时这一页仍然要站得住，不能因为空就藏掉入口。
 */
@Composable
fun UnrecognizedScreen(
    messages: List<UnrecognizedMessage>,
    mutedSenders: List<String>,
    now: Long,
    onBack: () -> Unit,
    onExtract: (UnrecognizedMessage) -> Unit,
    onDismiss: (UnrecognizedMessage) -> Unit,
    onMuteSender: (UnrecognizedMessage) -> Unit,
    onWriteRule: (UnrecognizedMessage) -> Unit,
    onUnmuteSender: (String) -> Unit,
) {
    val noSender = stringResource(R.string.unrecognized_no_sender)
    val parcel = messages.filter { it.domain == MessageDomain.PARCEL }
    val food = messages.filter { it.domain == MessageDomain.FOOD }
    var foodOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ScreenTopBar(title = stringResource(R.string.unrecognized_title), onBack = onBack)

        if (messages.isEmpty() && mutedSenders.isEmpty()) {
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
                    style = ShowCodeText.Section,
                    color = Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.unrecognized_empty_hint),
                    style = ShowCodeText.Meta,
                    color = InkMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (parcel.isEmpty() && food.isNotEmpty()) {
                    // 只剩外卖那些的时候不能再说「所有取件短信都识别成功」——
                    // 那是这句承诺最不该被说出口的场合。
                    item {
                        Text(
                            text = stringResource(R.string.unrecognized_no_parcel_left),
                            style = ShowCodeText.Section,
                            color = Ink,
                        )
                    }
                } else if (parcel.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_unrecognized_hint),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                        )
                    }
                }
                items(parcel, key = { it.id }) { message ->
                    UnrecognizedRow(
                        message = message,
                        now = now,
                        fallbackSender = noSender,
                        onExtract = { onExtract(message) },
                        onDismiss = { onDismiss(message) },
                        onMuteSender = { onMuteSender(message) },
                        onWriteRule = { onWriteRule(message) },
                    )
                }
                if (food.isNotEmpty()) {
                    item(key = "food-header") {
                        SectionHeader(
                            title = stringResource(R.string.unrecognized_food_section, food.size),
                            expanded = foodOpen,
                        ) { foodOpen = !foodOpen }
                    }
                    if (foodOpen) {
                        item(key = "food-hint") {
                            Text(
                                text = stringResource(R.string.unrecognized_food_hint),
                                style = ShowCodeText.Caption,
                                color = InkMuted,
                            )
                        }
                        items(food, key = { it.id }) { message ->
                            UnrecognizedRow(
                                message = message,
                                now = now,
                                fallbackSender = noSender,
                                onExtract = { onExtract(message) },
                                onDismiss = { onDismiss(message) },
                                onMuteSender = { onMuteSender(message) },
                                onWriteRule = { onWriteRule(message) },
                            )
                        }
                    }
                }
                if (mutedSenders.isNotEmpty()) {
                    item(key = "muted-header") {
                        Text(
                            text = stringResource(R.string.unrecognized_muted_title),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                        )
                    }
                    items(mutedSenders, key = { "mute-$it" }) { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceCard, ShowCodeShapes.Sm)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = key,
                                modifier = Modifier.weight(1f),
                                style = ShowCodeText.Meta,
                                color = InkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.unrecognized_unmute),
                                modifier = Modifier.clickable { onUnmuteSender(key) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                style = ShowCodeText.Action,
                                color = AccentInk,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Section,
            color = Ink,
        )
        Text(
            text = stringResource(if (expanded) R.string.unrecognized_collapse else R.string.unrecognized_expand),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnrecognizedRow(
    message: UnrecognizedMessage,
    now: Long,
    fallbackSender: String,
    onExtract: () -> Unit,
    onDismiss: () -> Unit,
    onMuteSender: () -> Unit,
    onWriteRule: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.sender.ifBlank { fallbackSender },
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Item,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTimeText(message.receivedAt, now),
                style = ShowCodeText.Micro,
                color = InkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = message.body, style = ShowCodeText.Meta, color = InkSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = unrecognizedReasonText(message.reason),
            style = ShowCodeText.Caption,
            color = UrgencyDueSoon,
        )
        Spacer(Modifier.height(10.dp))
        // 四个动作在 320dp 宽的屏上会挤过头一个标签被裁掉，所以让它换行而不是压成一排。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 4,
        ) {
            Text(
                text = stringResource(R.string.unrecognized_extract),
                modifier = Modifier.clickable(onClick = onExtract),
                style = ShowCodeText.Action,
                color = AccentInk,
            )
            // 手动提取救的是这一件，写规则救的是**这一类**。两个都是能走通的路，所以同色。
            Text(
                text = stringResource(R.string.unrecognized_write_rule),
                modifier = Modifier.clickable(onClick = onWriteRule),
                style = ShowCodeText.Action,
                color = AccentInk,
            )
            Text(
                text = stringResource(R.string.unrecognized_ignore),
                modifier = Modifier.clickable(onClick = onDismiss),
                style = ShowCodeText.Meta,
                color = InkMuted,
            )
            Text(
                text = stringResource(R.string.unrecognized_mute_sender),
                modifier = Modifier.clickable(onClick = onMuteSender),
                style = ShowCodeText.Meta,
                color = InkMuted,
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
        UnrecognizedReason.POINT_NAME_UNCONFIRMED -> R.string.unrecognized_point_unconfirmed
    }
)

/**
 * 手动导入 / 手动提取。拒绝短信权限之后这是一等公民退路，不是藏起来的备用功能
 * （edge s1 permission-sms-denied：不给一句「需要权限」，给一条能走通的路）。
 */
@Composable
fun ManualImportScreen(
    prefillRaw: String,
    /** 库里已经见过的正文（待取件的原文 + 还没处理的未识别正文）。自动填靠它决定「这条你已经导过了」。 */
    knownBodies: Set<String>,
    /** 库里已有的取件点名。手动录入时点一下就用，不用打那种二十个字的长名。 */
    knownPoints: List<String>,
    onBack: () -> Unit,
    onSave: suspend (code: String, pointName: String, lockerNo: String?, raw: String, owner: Owner) -> Boolean,
) {
    var raw by remember(prefillRaw) { mutableStateOf(prefillRaw) }
    var code by remember { mutableStateOf("") }
    var extras by remember { mutableStateOf<List<String>>(emptyList()) }
    var pointName by remember { mutableStateOf("") }
    var lockerNo by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf(Owner.SELF) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    /* 进屏自动填一次。判据里「草稿非空就不动」那一档正好也接住了从收容页带着原文进来的场合
       （那时 raw 已经是那条正文），所以这里不需要再补一个 `if (prefillRaw.isEmpty())`——
       补了反而是两道判据叠一处，将来改哪一边都不安全。
       自动这一条**不许出声**：填上了，文本自己会出现在框里；没填上，就什么都不说。 */
    LaunchedEffect(Unit) {
        val verdict = ClipboardIntake.autoPrefill(readText(context), draft = raw, knownBodies = knownBodies)
        if (verdict is ClipboardVerdict.Fill) raw = verdict.text
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
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
                // 显式粘贴：和进屏那一次自动填走的是**不同判据**（这条只拒空），
                // 也走不同的反馈——这一条读不到必须说，那一条读不到必须安静。
                Text(
                    text = stringResource(R.string.manual_import_paste),
                    modifier = Modifier.clickable {
                        when (val verdict = ClipboardIntake.explicitPaste(readText(context))) {
                            is ClipboardVerdict.Fill -> {
                                raw = verdict.text
                                error = null
                            }
                            is ClipboardVerdict.Skip ->
                                error = context.getString(R.string.manual_import_paste_empty)
                        }
                    },
                    style = ShowCodeText.Item,
                    color = AccentInk,
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
                                val first = outcome.parcels.first()
                                code = first.code
                                pointName = first.pointName
                                lockerNo = first.lockerNo.orEmpty()
                                extras = outcome.parcels.drop(1).map { it.code }
                                error = null
                            }
                            is ParseOutcome.Rejected -> {
                                // 码是确定的就先把码填上。这类消息缺的只是「在哪个点」，
                                // 让人重新抄一遍码等于把省下来的时间又还回去。
                                code = outcome.codes.firstOrNull().orEmpty()
                                extras = outcome.codes.drop(1)
                                error = if (outcome.reason == UnrecognizedReason.POINT_NAME_UNCONFIRMED) {
                                    context.getString(R.string.manual_import_auto_point_only)
                                } else {
                                    context.getString(R.string.manual_import_auto_failed)
                                }
                            }
                            ParseOutcome.Ignored ->
                                error = context.getString(R.string.manual_import_auto_failed)
                        }
                    },
                    style = ShowCodeText.Item,
                    color = AccentInk,
                )
            }
            if (extras.isNotEmpty()) {
                item {
                    // 一条里还有别的码就摆出来让人逐个点走。只填第一个，等于把第二件
                    // 重新变成看不见的——那正是这一整批在修的缺陷。
                    Text(
                        text = stringResource(R.string.manual_import_extra, extras.size),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        extras.forEach { extra ->
                            Text(
                                text = extra,
                                modifier = Modifier
                                    .background(SurfaceCard, ShowCodeShapes.Sm)
                                    .clickable {
                                        code = extra
                                        extras = extras - extra
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                style = ShowCodeText.Item,
                                color = Ink,
                            )
                        }
                    }
                }
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
            /* 已有点名直接点。手动录入的典型场景是「平台没推的那个件」，而那个驿站他手机里
               多半已经有别的件了——要点开键盘打「南昌工学院天工蓝菜鸟驿站驿站」这种长名，
               是不该存在的摩擦。形状沿用本屏上面那排码芯片，不另造一套。 */
            if (knownPoints.isNotEmpty()) {
                item {
                    // FlowRow 不是可选的：实测四个长中文点名一行装不下，
                    // 第三颗被截成「文昌苑菜鸟…」、第四颗直接跑到屏外点不到。
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        knownPoints.take(4).forEach { name ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .background(
                                        if (pointName == name) AccentTint else SurfaceCard,
                                        ShowCodeShapes.Sm,
                                    )
                                    .border(
                                        1.dp,
                                        if (pointName == name) AccentInk else Hairline,
                                        ShowCodeShapes.Sm,
                                    )
                                    .clickable { pointName = name }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                style = ShowCodeText.Caption,
                                color = if (pointName == name) Ink else InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = lockerNo,
                    onValueChange = { lockerNo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_import_locker)) },
                )
            }
            /* 归属在录入时就问一句，而不是事后去列表里改：
               妈妈发来的码之所以难处理，是因为它长得和自己的码一模一样。 */
            item {
                Column {
                    Text(
                        text = stringResource(R.string.owner_field),
                        style = ShowCodeText.Action,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OwnerChoice(
                            label = stringResource(R.string.owner_mine),
                            selected = owner == Owner.SELF,
                            onSelect = { owner = Owner.SELF },
                        )
                        OwnerChoice(
                            label = stringResource(R.string.owner_family),
                            selected = owner == Owner.FAMILY,
                            onSelect = { owner = Owner.FAMILY },
                        )
                    }
                }
            }
            item {
                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, ShowCodeShapes.Md)
                            .padding(12.dp),
                        style = ShowCodeText.Meta,
                        color = UrgencyDueSoon,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Ink, ShowCodeShapes.Md)
                        .clickable {
                            val trimmedCode = code.trim()
                            val trimmedName = pointName.trim()
                            if (trimmedCode.isEmpty() || trimmedName.isEmpty()) {
                                error = context.getString(R.string.manual_import_need_both)
                            } else {
                                // 以落库的返回为准，不能点下去就当成功。
                                scope.launch {
                                    val stored = onSave(
                                        trimmedCode,
                                        trimmedName,
                                        lockerNo.trim().takeIf { it.isNotEmpty() },
                                        raw,
                                        owner,
                                    )
                                    if (!stored) error = context.getString(R.string.manual_import_save_failed)
                                }
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.manual_import_save),
                        style = ShowCodeText.Section,
                        color = OnsiteSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .background(if (selected) AccentTint else Canvas, ShowCodeShapes.Md)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Ink else InkMuted,
                shape = ShowCodeShapes.Md,
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = ShowCodeText.Item,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = Ink,
    )
}

/**
 * 子屏的顶栏。`onBack` 为空时不画那颗返回箭头——底栏那三格是主屏，不是谁的子页。
 *
 * 没箭头时标题从 20dp 起，对准的是**首页**标题的起位而不是子屏的：主屏之间对齐比
 * 主屏跟子屏对齐重要，同一层的两屏标题错开一截，用户会读成「这两屏不是平级的」。
 */
@Composable
internal fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onsite_back))
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            style = ShowCodeText.Headline,
            color = Ink,
        )
    }
}
