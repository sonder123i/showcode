package dev.showcode.ui.points

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.MergePreview
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointAlias
import dev.showcode.core.SuspectPair
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.resolvePointKey
import dev.showcode.core.urgencyOf
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.FAMILY_ICON
import dev.showcode.ui.urgencyColor
import dev.showcode.ui.urgencyLabel
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.Canvas
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.environmentBackground

/**
 * story-6 的三个表面：疑似重复轻提示、合并预览确认、取件点详情（含合并历史与拆开）。
 *
 * 这条故事的验收点是**用户在按下合并之前就知道能撤销**，所以「可以随时拆开」那句话
 * 出现在确认按钮之上，而不是失败之后才给。
 */

/** 首页的疑似重复提示：inline 卡片，不弹 modal，绝不自动合并（AC4 的红线）。 */
@Composable
fun SuspectHintCard(pair: SuspectPair, onYes: () -> Unit, onNo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.merge_suspect, pair.first.name, pair.second.name),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Meta,
            color = Ink,
        )
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onNo) {
            Text(stringResource(R.string.merge_suspect_no), style = ShowCodeText.Meta)
        }
        TextButton(onClick = onYes) {
            Text(
                text = stringResource(R.string.merge_suspect_yes),
                style = ShowCodeText.Action,
            )
        }
    }
}

/**
 * 合并预览（AC1）。给的是证据：两边各多少件、合计多少件、两边各自最近的原始写法，
 * 让用户用眼睛判断而不是凭记忆。名字留哪个是这条流程里唯一不可自动化的决定。
 */
/**
 * 合并预览确认（AC1）——整屏，不是弹层。
 * edge 与 stories 都把它记作 `screen:`；而实测证明这不是措辞问题：
 * ModalBottomSheet 被限到半屏，「确认合并」落在折叠线以下，
 * 一个要先拖弹层才按得到的确认键等于把主路径藏起来。
 */
@Composable
fun MergePreviewScreen(
    preview: MergePreview,
    keepTarget: Boolean,
    onKeepTarget: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().environmentBackground(),
        containerColor = Color.Transparent,
        contentColor = Ink,
        topBar = { ScreenTopBar(title = stringResource(R.string.merge_preview_title), onBack = onCancel) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.merge_preview_body,
                        preview.source.name,
                        preview.source.count,
                        preview.target.name,
                        preview.target.count,
                        preview.total,
                    ),
                    style = ShowCodeText.Body,
                    color = Ink,
                )
                Spacer(Modifier.height(14.dp))
                NameSamples(preview)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.merge_preview_reassure),
                    style = ShowCodeText.Meta,
                    color = InkSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.merge_preview_keep),
                    style = ShowCodeText.Action,
                    color = InkMuted,
                )
                KeepOption(
                    label = preview.target.name,
                    count = preview.target.count,
                    selected = keepTarget,
                    onSelect = { onKeepTarget(true) },
                )
                KeepOption(
                    label = preview.source.name,
                    count = preview.source.count,
                    selected = !keepTarget,
                    onSelect = { onKeepTarget(false) },
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = ShowCodeShapes.Md,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = OnsiteSurface,
                ),
            ) {
                Text(stringResource(R.string.merge_confirm), style = ShowCodeText.Section, fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 20.dp),
            ) {
                Text(stringResource(R.string.action_cancel), style = ShowCodeText.Body, color = InkMuted)
            }
        }
    }
}

@Composable
private fun NameSamples(preview: MergePreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.merge_preview_names),
            style = ShowCodeText.Caption,
            color = InkMuted,
        )
        Spacer(Modifier.height(6.dp))
        preview.source.nameSamples.forEach { SampleRow(it) }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            thickness = 1.dp,
            color = InkMuted,
        )
        preview.target.nameSamples.forEach { SampleRow(it) }
    }
}

@Composable
private fun SampleRow(name: String) {
    Text(
        text = name,
        style = ShowCodeText.Body,
        color = Ink,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun KeepOption(label: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = ShowCodeText.Body, color = Ink)
        Text(
            text = stringResource(R.string.merge_side_count, count),
            style = ShowCodeText.Meta,
            color = InkMuted,
        )
    }
}

/** 合并后的 5 秒撤销条。视觉与现场模式的「已标记取出」同族——反悔的入口不该长得不像反悔。 */
@Composable
fun MergeUndoBar(keptName: String, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink, ShowCodeShapes.Md)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.merge_done, keptName),
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Item,
            color = OnsiteSurface,
        )
        Text(
            text = stringResource(R.string.onsite_undo),
            modifier = Modifier
                .clickable(onClick = onUndo)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = ShowCodeText.Item,
            color = OnsiteSurface,
        )
    }
}

/**
 * 取件点详情。入口是卡片右上角的 ⋮，不是卡片本身——
 * 点卡片直进现场模式那个 1 步承诺不能被管理功能污染。
 */
@Composable
private fun OwnerChip(
    label: String,
    highlighted: Boolean,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            // 代取件是别人转来的码，归属这一档必须有图形：它在管理面里是唯一
            // 「这件不是我的」的信号，光靠两个字在扫视时太慢。
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (highlighted) Ink else InkMuted,
            )
            Spacer(Modifier.width(5.dp))
        }
    Text(
        text = label,
        modifier = Modifier
            .background(if (highlighted) AccentTint else Canvas, ShowCodeShapes.Md)
            .border(
                width = 1.dp,
                color = if (highlighted) Ink else InkMuted,
                shape = ShowCodeShapes.Sm,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = ShowCodeText.Caption,
        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
        color = if (highlighted) Ink else InkMuted,
    )
    }
}

@Composable
fun PointDetailScreen(
    group: PickupPointGroup?,
    history: List<PointAlias>,
    otherGroups: List<PickupPointGroup>,
    aliases: Map<String, PointAlias>,
    now: Long,
    onBack: () -> Unit,
    onOpenPoint: (PickupPointGroup) -> Unit,
    onSplit: (PointAlias) -> Unit,
    onMergeOtherIntoThis: (PickupPointGroup) -> Unit,
    onSetOwner: (Parcel, Owner) -> Unit,
) {
    val name = group?.name ?: history.firstOrNull()?.targetName.orEmpty()
    Scaffold(
        modifier = Modifier.fillMaxSize().environmentBackground(),
        containerColor = Color.Transparent,
        contentColor = Ink,
        topBar = { ScreenTopBar(title = name, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (group == null) {
                Text(
                    text = stringResource(R.string.detail_gone),
                    style = ShowCodeText.Body,
                    color = InkMuted,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.detail_pending, group.items.size),
                    style = ShowCodeText.Action,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                group.items.forEach { parcel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPoint(group) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = parcel.code + (parcel.lockerNo?.let { "  $it" } ?: ""),
                            modifier = Modifier.weight(1f),
                            style = ShowCodeText.Section,
                            color = Ink,
                        )
                        Text(
                            text = urgencyLabel(urgencyOf(parcel, now)),
                            style = ShowCodeText.Action,
                            color = urgencyColor(urgencyOf(parcel, now)),
                        )
                        /* 归属切换放在管理面，不放现场：站在柜机前不应该
                           顺手改一件件的所属。 */
                        Spacer(Modifier.width(8.dp))
                        OwnerChip(
                            label = stringResource(
                                if (parcel.owner == Owner.FAMILY) R.string.detail_owner_family
                                else R.string.detail_owner_mine
                            ),
                            icon = if (parcel.owner == Owner.FAMILY) FAMILY_ICON else null,
                            highlighted = parcel.owner == Owner.FAMILY,
                            onClick = {
                                onSetOwner(
                                    parcel,
                                    if (parcel.owner == Owner.FAMILY) Owner.SELF else Owner.FAMILY,
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.detail_owner_hint),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(thickness = 1.dp, color = InkMuted)

            Text(
                text = stringResource(R.string.detail_history),
                style = ShowCodeText.Action,
                color = InkMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_history_empty),
                    style = ShowCodeText.Body,
                    color = InkMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                history.forEach { alias ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.detail_history_row, alias.aliasName),
                            modifier = Modifier.weight(1f),
                            style = ShowCodeText.Body,
                            color = Ink,
                        )
                        Text(
                            text = relativeTimeText(alias.createdAt, now),
                            style = ShowCodeText.Caption,
                            color = InkMuted,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onSplit(alias) }) {
                            Text(stringResource(R.string.detail_split), style = ShowCodeText.Item, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.detail_split_hint),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = InkMuted)
            Text(
                text = stringResource(R.string.detail_others),
                style = ShowCodeText.Action,
                color = InkMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.detail_others_hint),
                style = ShowCodeText.Caption,
                color = InkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // 轻提示只报字面重叠的一对，宁可漏报；所以合并也要能按名字自己找到入口，
            // 否则「我知道它们是同一个点」这件事就没有出口。
            otherGroups
                .filter { resolvePointKey(it.key, aliases) != group?.key }
                .forEach { other ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = other.name, modifier = Modifier.weight(1f), style = ShowCodeText.Body, color = Ink)
                        Text(
                            text = stringResource(R.string.merge_side_count, other.items.size),
                            style = ShowCodeText.Meta,
                            color = InkMuted,
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { onMergeOtherIntoThis(other) }) {
                            Text(stringResource(R.string.detail_merge_into), style = ShowCodeText.Item, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            Spacer(Modifier.height(28.dp))
        }
    }
}
