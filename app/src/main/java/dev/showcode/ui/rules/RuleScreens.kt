package dev.showcode.ui.rules

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.core.Source
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseRule
import dev.showcode.core.parse.ParseRules
import dev.showcode.core.parse.RuleCheck
import dev.showcode.core.parse.RuleReject
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* 保存成功后那句「已保存」的驻留时长。不给这一眼就直接跳屏，用户会怀疑自己刚才那一下
   到底存没存上；给太久又变成卡在中间——这一屏的下一站本来就是列表。 */
private const val RULE_SAVED_HOLD_MS = 900L

/**
 * 解析规则列表（edge：用户自定义解析规则）。
 *
 * 这一屏存在的理由是「写进去的东西必须看得见、删得掉」——和屏蔽名单同一条理由。
 * 一条规则最坏的失效是**看起来在工作、其实永不命中**，所以列表不能只显示名字：
 * 它得把正则本体摊开，让半年后回来清理的人认出那是哪一条。
 */
@Composable
fun RuleListScreen(
    rules: List<ParseRule>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.rules_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.rules_hint, ParseRules.MAX_RULES),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
            }
            if (rules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.rules_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, ShowCodeShapes.Md)
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(16.dp),
                        style = ShowCodeText.Meta,
                        color = InkSecondary,
                    )
                }
            }
            items(rules, key = { it.id }) { rule ->
                RuleRow(rule = rule, onToggle = onToggle, onDelete = onDelete)
            }
            item {
                Text(
                    text = stringResource(R.string.rules_add),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, Accent, ShowCodeShapes.Md)
                        .clickable(onClick = onAdd)
                        .padding(16.dp),
                    style = ShowCodeText.Item,
                    color = Accent,
                )
            }
        }
    }
}

@Composable
private fun RuleRow(rule: ParseRule, onToggle: (Long, Boolean) -> Unit, onDelete: (Long) -> Unit) {
    // 删除是两次点击的事：这条规则可能是他照着一条难短信调了半小时调出来的，
    // 而手滑一下就能把它清掉——「误删只能重写」不是我们能接受的回退成本。
    var confirming by remember(rule.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rule.pointName,
                modifier = Modifier.weight(1f),
                style = ShowCodeText.Item,
                color = if (rule.enabled) Ink else InkMuted,
            )
            Text(
                text = stringResource(
                    if (rule.enabled) R.string.rules_on else R.string.rules_off,
                ),
                modifier = Modifier
                    .background(
                        color = if (rule.enabled) UrgencySafe.copy(alpha = 0.12f) else SurfaceCard,
                        shape = ShowCodeShapes.Sm,
                    )
                    .clickable { onToggle(rule.id, !rule.enabled) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = ShowCodeText.Action,
                color = if (rule.enabled) UrgencySafe else InkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = rule.pattern,
            style = ShowCodeText.CodeSmall,
            color = InkSecondary,
        )
        if (rule.lockerNo != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.rules_locker, rule.lockerNo),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (confirming) R.string.rules_delete_confirm else R.string.rules_delete),
            modifier = Modifier
                .clickable {
                    if (confirming) onDelete(rule.id) else confirming = true
                }
                .padding(vertical = 4.dp),
            style = ShowCodeText.Action,
            color = if (confirming) UrgencyDueSoon else InkMuted,
        )
    }
}

/**
 * 写一条规则。
 *
 * 三个字段里只有两个是必填（码 + 点名），正则那一格**由 App 生成**——
 * 竞品 issue 里最一致的抱怨就是「不知道该写什么正则」，所以这一屏不能把它做成一个正则输入框。
 * 手写的出口仍然留着（生成失败、或想收紧命中范围时用）。
 *
 * 保存前必须当场预览：一条永不命中的规则存进列表之后，从任何界面看都像是在工作的。
 */
@Composable
fun RuleEditScreen(
    prefillRaw: String,
    now: Long,
    onBack: () -> Unit,
    onCheck: (pattern: String, pointName: String, sample: IncomingMessage) -> RuleCheck,
    onSave: suspend (pattern: String, pointName: String, lockerNo: String?) -> Boolean,
) {
    /* 调一条规则要对着原文反复改、反复看预览，中间还会切屏去对照那条短信。
       这些东西用 remember 存等于转一下屏幕就把半小时清空，所以整屏都走 Saveable。 */
    var raw by rememberSaveable(prefillRaw) { mutableStateOf(prefillRaw) }
    var code by rememberSaveable { mutableStateOf("") }
    var pointName by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf("") }
    var lockerNo by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var savedFlag by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 预览跟着输入实时算：这一屏的全部价值就在「保存之前就知道它生不生效」。
    // receivedAt 用现在这个 tick 就够——规则不看时间，但走的是同一个解析器，不能给它一个假字段。
    val verdict = remember(pattern, pointName, raw) {
        if (pattern.isBlank() || raw.isBlank()) {
            null
        } else {
            onCheck(
                pattern,
                pointName,
                IncomingMessage(sender = "", body = raw, receivedAt = now, channel = Source.MANUAL),
            )
        }
    }

    // 保存的闸门挂在预览上，不挂在校验上：正则能编译、点名不空，仍然可能是一条永不命中的规则，
    // 而这一屏的样本原文就摆在同一个屏里——它对这条不起作用，就说明规则还没写完。
    val canSave = verdict is RuleCheck.WouldAdd

    LaunchedEffect(savedFlag) {
        if (savedFlag) {
            delay(RULE_SAVED_HOLD_MS)
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.rules_edit_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.rules_edit_hint),
                    style = ShowCodeText.Meta,
                    color = InkMuted,
                )
            }
            item {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rules_field_raw)) },
                    minLines = 3,
                )
            }
            item {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rules_field_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            item {
                OutlinedTextField(
                    value = pointName,
                    onValueChange = { pointName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rules_field_point)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = lockerNo,
                    onValueChange = { lockerNo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rules_field_locker)) },
                    singleLine = true,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.rules_generate),
                    modifier = Modifier
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, Accent, ShowCodeShapes.Md)
                        .clickable {
                            val generated = ParseRules.literalRule(raw, code.trim(), pointName)
                            if (generated == null) {
                                notice = context.getString(R.string.rules_generate_failed)
                            } else {
                                pattern = generated.pattern
                                notice = null
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    style = ShowCodeText.Action,
                    color = Accent,
                )
            }
            item {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rules_field_pattern)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            notice?.let { message ->
                item { VerdictBlock(text = message, ok = false) }
            }
            verdict?.let { check ->
                item {
                    VerdictBlock(
                        text = verdictText(check),
                        ok = check is RuleCheck.WouldAdd,
                    )
                }
            }
            if (savedFlag) {
                item { VerdictBlock(text = stringResource(R.string.rules_saved), ok = true) }
            }
            item {
                Text(
                    text = stringResource(R.string.rules_save),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(if (canSave) Ink else SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, if (canSave) Ink else Hairline, ShowCodeShapes.Md)
                        .clickable(enabled = canSave) {
                            scope.launch {
                                val stored = onSave(pattern.trim(), pointName, lockerNo)
                                // 以落库的返回为准，不能点下去就当成功：失败时字要留在屏上。
                                if (stored) savedFlag = true
                                else notice = context.getString(R.string.rules_save_failed)
                            }
                        },
                    style = ShowCodeText.Section,
                    color = if (canSave) OnsiteSurface else InkMuted,
                )
            }
        }
    }
}

@Composable
private fun VerdictBlock(text: String, ok: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, if (ok) UrgencySafe else UrgencyDueSoon, ShowCodeShapes.Md)
            .padding(12.dp),
        style = ShowCodeText.Meta,
        color = if (ok) UrgencySafe else UrgencyDueSoon,
    )
}

/** 每一档结论对应一种不同的修法，所以不能压成一句「预览失败」。 */
@Composable
private fun verdictText(check: RuleCheck): String = when (check) {
    is RuleCheck.WouldAdd -> stringResource(R.string.rules_verdict_would_add, check.code)
    is RuleCheck.Invalid -> when (check.reject) {
        // 上限要报出真实数字：不说「太长」到哪一步该收手，用户没法改。
        RuleReject.PATTERN_TOO_LONG -> stringResource(R.string.rules_reject_too_long, ParseRules.MAX_PATTERN_LENGTH)
        else -> stringResource(rejectText(check.reject))
    }
    RuleCheck.NoHit -> stringResource(R.string.rules_verdict_no_hit)
    is RuleCheck.NotCodeShape -> stringResource(R.string.rules_verdict_bad_shape, check.matched)
    RuleCheck.MaskedByBuiltIn -> stringResource(R.string.rules_verdict_masked)
    RuleCheck.NoPickupContext -> stringResource(R.string.rules_verdict_no_context)
}

private fun rejectText(reject: String): Int = when (reject) {
    RuleReject.EMPTY_PATTERN -> R.string.rules_reject_empty_pattern
    RuleReject.PATTERN_TOO_LONG -> R.string.rules_reject_too_long
    RuleReject.EMPTY_POINT_NAME -> R.string.rules_reject_empty_point
    RuleReject.POINT_NAME_TOO_LONG -> R.string.rules_reject_point_too_long
    RuleReject.TOO_MANY_RULES -> R.string.rules_reject_too_many
    else -> R.string.rules_reject_syntax
}
