package dev.showcode.ui.identity

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.annotation.StringRes
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.showcode.R
import dev.showcode.core.ClipboardIntake
import dev.showcode.core.ClipboardVerdict
import dev.showcode.core.IdentityCode
import dev.showcode.core.IdentityKind
import dev.showcode.core.DeepLinkLadder
import dev.showcode.core.IdentityCodes
import dev.showcode.core.IdentityReject
import dev.showcode.core.LaunchOutcome
import dev.showcode.ui.LaunchOutcomeLine
import dev.showcode.ui.navBarClearance
import dev.showcode.core.qr.MAX_VERSION
import dev.showcode.core.qr.Qr
import dev.showcode.data.InstalledApp
import dev.showcode.ui.readText
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.AccentInk
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* 「已存好」那句的驻留时长。和解析规则那一屏同一个数：两处都是「存完就回列表」，
   停的时间不一样会让人以为两个屏的规则不一样。 */
private const val IDENTITY_SAVED_HOLD_MS = 900L

/**
 * 身份码列表。
 *
 * 这里列的是**要扫的东西**，不是包裹，所以没有紧急度、没有排序判据——
 * 一屏最多 [IdentityCodes.MAX_CODES] 条，按录入时间排就够了。
 */
@Composable
fun IdentityListScreen(
    codes: List<IdentityCode>,
    entries: List<String>,
    launch: LaunchOutcome?,
    onAdd: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onShow: (IdentityCode) -> Unit,
    onToggleDynamic: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.identity_title))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBarClearance()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            /* 预置的那几家：点一下直接打开它，不落库、不进出示屏。
               存在的理由是把最常见的两条路压到一下——存一张「入口」再省也要四下，
               而竞品那一格本来就只有跳转。该列哪几家是判据（只列真装着的），在 core 那边筛。 */
            if (entries.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, ShowCodeShapes.Md)
                            .border(1.dp, Hairline, ShowCodeShapes.Md)
                            .padding(bottom = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.identity_entries_hint),
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                        )
                        entries.forEach { pkg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEntry(pkg) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(presetLabel(pkg)),
                                    modifier = Modifier.weight(1f),
                                    style = ShowCodeText.Item,
                                    color = Ink,
                                )
                                Text(
                                    text = stringResource(R.string.identity_entry_open),
                                    style = ShowCodeText.Action,
                                    color = AccentInk,
                                )
                            }
                        }
                        // 按下去的结局就住在同一张卡里：话要说在刚按的那一下旁边。
                        LaunchOutcomeLine(launch, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.identity_hint),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
            }
            if (codes.isEmpty()) {
                item {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, Hairline, ShowCodeShapes.Md)
                        .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.identity_empty),
                            style = ShowCodeText.Meta,
                            color = InkSecondary,
                        )
                        // 这句只在一家预置都没出现时才是路标；预置卡就在上面时它是废话。
                        if (entries.isEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.identity_empty_no_presets),
                                style = ShowCodeText.Caption,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
            items(codes, key = { it.id }) { code ->
                IdentityRow(
                    code = code,
                    onShow = { onShow(code) },
                    onToggleDynamic = { onToggleDynamic(code.id, !code.dynamic) },
                    onDelete = onDelete,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.identity_add),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, AccentInk, ShowCodeShapes.Md)
                        .clickable(onClick = onAdd)
                        .padding(16.dp),
                    style = ShowCodeText.Item,
                    color = AccentInk,
                )
            }
        }
    }
}

@Composable
private fun IdentityRow(
    code: IdentityCode,
    onShow: () -> Unit,
    onToggleDynamic: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    var confirming by remember(code.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = code.label,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onShow),
                style = ShowCodeText.Item,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 「会变」不是标签，是警告：它意味着这张屏上那张码可能已经作废。
            // 入口那一类没有会作废的内容——给它标一个「固定」是假的安心。
            Text(
                text = stringResource(
                    when {
                        code.kind == IdentityKind.ENTRY -> R.string.identity_kind_entry_badge
                        code.dynamic -> R.string.identity_dynamic
                        else -> R.string.identity_static
                    }
                ),
                modifier = Modifier
                    .background(
                        color = if (code.dynamic) UrgencyDueSoon.copy(alpha = 0.12f) else SurfaceCard,
                        shape = ShowCodeShapes.Sm,
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = ShowCodeText.Micro,
                color = if (code.dynamic) UrgencyDueSoon else InkMuted,
            )
        }
        // 入口那一行没有码可显示：留一个空行会让人以为它没存上。
        if (code.kind == IdentityKind.CODE) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = code.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShow),
                style = ShowCodeText.CodeSmall,
                color = InkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        code.note?.let { note ->
            Spacer(Modifier.height(4.dp))
            Text(text = note, style = ShowCodeText.Caption, color = InkMuted)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(
                    if (code.kind == IdentityKind.CODE) R.string.identity_show else R.string.identity_go
                ),
                modifier = Modifier.clickable(onClick = onShow),
                style = ShowCodeText.Action,
                color = AccentInk,
            )
            // 「会不会变」是码的属性，不是入口的属性。给入口这一颗，点下去就是把
            // dynamic 写成 1，而那张屏上没有码可作废——提醒会变成一句没来由的谎。
            if (code.kind == IdentityKind.CODE) {
                Text(
                    text = stringResource(if (code.dynamic) R.string.identity_mark_static else R.string.identity_mark_dynamic),
                    modifier = Modifier.clickable(onClick = onToggleDynamic),
                    style = ShowCodeText.Action,
                    color = InkMuted,
                )
            }
            Text(
                text = stringResource(if (confirming) R.string.identity_delete_confirm else R.string.identity_delete),
                modifier = Modifier
                    .clickable { if (confirming) onDelete(code.id) else confirming = true }
                    .padding(vertical = 4.dp),
                style = ShowCodeText.Action,
                color = if (confirming) UrgencyDueSoon else InkMuted,
            )
        }
    }
}

/**
 * 存一张身份码。
 *
 * 必填的其实只有两格，而**「会不会变」是这一屏最重要的一格**：静态码存下来是资产，
 * 会话级动态码存下来是一颗定时炸弹——两张屏看起来一模一样，区别只在用户站在自助机前那一次。
 * 所以这一格不给默认值，必须点一下才让走。
 */
@Composable
fun IdentityEditScreen(
    existingCount: Int,
    apps: List<InstalledApp>,
    onBack: () -> Unit,
    onSave: suspend (label: String, value: String, kind: IdentityKind, dynamic: Boolean, note: String?, appPackage: String?) -> Boolean,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(IdentityKind.CODE) }
    var dynamic by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var app by rememberSaveable { mutableStateOf<InstalledApp?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var savedFlag by rememberSaveable { mutableStateOf(false) }
    /** 只装一句「按下了但那一下没结果」的话（剪贴板读不到）。校验失败走 reject，两条不混。 */
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 入口那一类的名字就是那家 App 的名字（PackageManager 给的显示名），不再让人打一遍。
    val effectiveLabel = if (kind == IdentityKind.ENTRY) app?.label.orEmpty() else label
    // 判据和写路径同一个（真的编一遍码），实时算，所以「现在还不能存」的原因在保存之前就看得见。
    val reject = remember(effectiveLabel, value, kind, dynamic, app, existingCount) {
        IdentityCodes.validate(
            label = effectiveLabel,
            value = value,
            kind = kind,
            dynamic = dynamic,
            appPackage = app?.packageName,
            existingCount = existingCount,
        )
    }
    val matrix = remember(value) { Qr.of(value.trim()) }

    LaunchedEffect(savedFlag) {
        if (savedFlag) {
            delay(IDENTITY_SAVED_HOLD_MS)
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.identity_edit_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 这句整段都在讲「复制码本身、别存截图」——选了入口这一类时这一屏根本没有码可复制，
            // 挂着它就是在对着一句不存在的话做指示。入口的说明是下面那条 kind 提示。
            if (kind == IdentityKind.CODE) {
                item {
                    Text(
                        text = stringResource(R.string.identity_edit_hint),
                        style = ShowCodeText.Meta,
                        color = InkMuted,
                    )
                }
            }
            /* 先问存什么，因为它决定后面那几格存不存在。
               竞品那一格本来就只有跳转（「只是跳转。身份码是动态刷新的」），
               而我们过去只能存一张码、跳转当附属——主次是反的。 */
            item {
                Column {
                    Text(
                        text = stringResource(R.string.identity_kind_question),
                        style = ShowCodeText.Action,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice(
                            label = stringResource(R.string.identity_kind_code),
                            selected = kind == IdentityKind.CODE,
                            onSelect = { kind = IdentityKind.CODE },
                        )
                        Choice(
                            label = stringResource(R.string.identity_kind_entry),
                            selected = kind == IdentityKind.ENTRY,
                            onSelect = { kind = IdentityKind.ENTRY },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (kind == IdentityKind.CODE) R.string.identity_kind_code_hint
                            else R.string.identity_kind_entry_hint
                        ),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
            }
            // 入口那一类的名字就是那家 App 的名字：为已经选好的东西再打一遍标题是收税。
            if (kind == IdentityKind.CODE) {
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.identity_field_label)) },
                        singleLine = true,
                    )
                }
            }
            if (kind == IdentityKind.CODE) {
                item {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.identity_field_value)) },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
                item {
                    /* 粘贴优先于打字：身份码来自别的 App 的页面或截图，手打错一位，
                       得到的是一张**合法、能扫、扫出来是错内容**的码——这种错当场看不出来，
                       要等到柜机前才发现。所以这一格默认要有一条不用打字的入口。
                       判据复用 F3 那一条 explicitPaste（按下就是表达意图，只拒空），
                       而不是在这里再写一套「像不像一条码」的猜测。 */
                    Text(
                        text = stringResource(R.string.identity_paste),
                        modifier = Modifier
                            .clickable {
                                when (val verdict = ClipboardIntake.explicitPaste(readText(context))) {
                                    is ClipboardVerdict.Fill -> value = verdict.text
                                    is ClipboardVerdict.Skip ->
                                        notice = context.getString(R.string.identity_paste_empty)
                                }
                            }
                            .padding(vertical = 6.dp),
                        style = ShowCodeText.Action,
                        color = AccentInk,
                    )
                }
            }
            if (kind == IdentityKind.CODE && value.isNotBlank()) {
                // 码值一进来就先把「能不能画成码」说清楚，因为那是这一屏唯一的失效方式。
                // 报版本不报字符数：能塞进 15 版的长度取决于内容是数字还是汉字，说数字是误导。
                item {
                    Text(
                        text = matrix?.let {
                            stringResource(R.string.identity_qr_ok, it.version, MAX_VERSION)
                        } ?: stringResource(R.string.identity_qr_too_big),
                        style = ShowCodeText.Caption,
                        color = if (matrix == null) UrgencyDueSoon else InkMuted,
                    )
                }
            }
            if (kind == IdentityKind.CODE) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.identity_dynamic_question),
                            style = ShowCodeText.Action,
                            color = InkMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(
                                label = stringResource(R.string.identity_dynamic_no),
                                selected = dynamic == false,
                                onSelect = { dynamic = false },
                            )
                            Choice(
                                label = stringResource(R.string.identity_dynamic_yes),
                                selected = dynamic == true,
                                onSelect = { dynamic = true },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.identity_dynamic_question_hint),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                        )
                    }
                }
            }
            item {
                Column {
                    Text(
                        text = app?.let { stringResource(R.string.identity_app_chosen, it.label) }
                            ?: stringResource(R.string.identity_app_none),
                        style = ShowCodeText.Meta,
                        color = Ink,
                    )
                    // 已知那几家排在这儿：绝大多数人就是这两家，让他们在几百行里搜「菜鸟」
                    // 是把三秒的事做成三十秒。**只列真装着的**——一颗点了只会失败的按钮比没有更坏。
                    val quick = remember(apps) {
                        // 筛法只有一份（core 的 entriesFor）：同一个判据在两个屏上各写一遍，
                        // 迟早会变成「那一屏列了两家、这一屏列了一家」。
                        DeepLinkLadder.entriesFor(apps.mapTo(mutableSetOf()) { it.packageName })
                            .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
                    }
                    if (quick.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quick.forEach { option ->
                                Choice(
                                    label = stringResource(presetLabel(option.packageName)),
                                    selected = app?.packageName == option.packageName,
                                    onSelect = { app = option },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = stringResource(R.string.identity_app_pick),
                            modifier = Modifier
                                .clickable { picking = true }
                                .padding(vertical = 4.dp),
                            style = ShowCodeText.Action,
                            color = AccentInk,
                        )
                        if (app != null) {
                            Text(
                                text = stringResource(R.string.identity_app_clear),
                                modifier = Modifier
                                    .clickable { app = null }
                                    .padding(vertical = 4.dp),
                                style = ShowCodeText.Action,
                                color = InkMuted,
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            if (kind == IdentityKind.CODE) R.string.identity_app_hint
                            else R.string.identity_app_hint_entry
                        ),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.identity_field_note)) },
                    singleLine = true,
                )
            }
            notice?.let { message ->
                item {
                    Text(
                        text = message,
                        style = ShowCodeText.Meta,
                        color = InkMuted,
                    )
                }
            }
            if (reject != null && value.isNotBlank()) {
                item {
                    Text(
                        text = stringResource(rejectText(reject)),
                        style = ShowCodeText.Meta,
                        color = UrgencyDueSoon,
                    )
                }
            }
            if (savedFlag) {
                item {
                    Text(
                        text = stringResource(R.string.identity_saved),
                        style = ShowCodeText.Meta,
                        color = UrgencySafe,
                    )
                }
            }
            item {
                val canSave = reject == null
                Text(
                    text = stringResource(R.string.identity_save),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(if (canSave) Ink else SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, if (canSave) Ink else Hairline, ShowCodeShapes.Md)
                        .clickable(enabled = canSave) {
                            scope.launch {
                                val stored = onSave(effectiveLabel, value, kind, dynamic == true, note, app?.packageName)
                                // 以落库的返回为准：失败时字要留在屏上。
                                if (stored) savedFlag = true
                            }
                        },
                    style = ShowCodeText.Section,
                    color = if (canSave) OnsiteSurface else InkMuted,
                )
            }
        }
    }

    if (picking) {
        Dialog(onDismissRequest = { picking = false }) {
            AppPicker(
                apps = apps,
                onPick = { picked ->
                    app = picked
                    // 名字空着的时候替他填上：从「菜鸟驿站」选出来的那张，标题八成就叫菜鸟驿站。
                    if (label.isBlank()) label = picked.label.take(IdentityCodes.MAX_LABEL_LENGTH)
                    picking = false
                },
                onCancel = { picking = false },
            )
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .background(if (selected) Accent else Canvas, ShowCodeShapes.Md)
            .border(1.dp, if (selected) Ink else Hairline, ShowCodeShapes.Md)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = ShowCodeText.Item,
        color = Ink,
    )
}

/**
 * 从这台手机装过的 App 里挑一个。
 *
 * 不做「手填包名」那一格：`com.cainiao.android` 这种东西没人记得住，
 * 而这一格要救的恰恰是**码已经失效、需要回官方 App 现取**的那次——最不该让人打字的一次。
 */
@Composable
private fun AppPicker(apps: List<InstalledApp>, onPick: (InstalledApp) -> Unit, onCancel: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .background(OnsiteSurface, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.identity_app_picker_title),
            style = ShowCodeText.Section,
            color = Ink,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.identity_app_picker_search)) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            // 空列表要说清是「这台手机没装」还是「搜不到」，否则用户会以为是自己操作错了。
            Text(
                text = stringResource(
                    if (apps.isEmpty()) R.string.identity_app_picker_none_installed
                    else R.string.identity_app_picker_no_match
                ),
                style = ShowCodeText.Meta,
                color = InkMuted,
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            items(shown, key = { it.packageName }) { app ->
                Text(
                    text = app.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(app) }
                        .padding(vertical = 12.dp),
                    style = ShowCodeText.Item,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.identity_app_picker_cancel),
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(vertical = 6.dp),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
        }
    }
}

/**
 * 快捷项的显示名走字符串资源，不写进 core：core 那份表是**判据**（哪家有直达链），
 * 而「淘宝」两个字是文案。把两处合在一起的代价是改名要动判据层。
 *
 * 这里不直接用 `InstalledApp.label`（那是 PackageManager 给的名字，会随语言和厂商变，
 * 有时是「手机淘宝」有时是「淘宝」）——这一格是预置项，名字该由我们定。
 */
@StringRes
private fun presetLabel(packageName: String): Int = when (packageName) {
    DeepLinkLadder.TAOBAO -> R.string.preset_taobao
    DeepLinkLadder.PINDUODUO -> R.string.preset_pinduoduo
    DeepLinkLadder.CAINIAO -> R.string.preset_cainiao
    DeepLinkLadder.FENGCHAO -> R.string.preset_fengchao
    else -> R.string.preset_generic
}

private fun rejectText(reject: String): Int = when (reject) {
    IdentityReject.NEEDS_DYNAMIC -> R.string.identity_reject_needs_dynamic
    IdentityReject.NEEDS_PACKAGE -> R.string.identity_reject_needs_package
    IdentityReject.EMPTY_LABEL -> R.string.identity_reject_empty_label
    IdentityReject.LABEL_TOO_LONG -> R.string.identity_reject_label_long
    IdentityReject.EMPTY_VALUE -> R.string.identity_reject_empty_value
    IdentityReject.VALUE_NOT_ENCODABLE -> R.string.identity_reject_not_encodable
    else -> R.string.identity_reject_too_many
}
