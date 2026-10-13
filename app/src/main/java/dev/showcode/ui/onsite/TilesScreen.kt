package dev.showcode.ui.onsite

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.Parcel
import dev.showcode.core.Urgency
import dev.showcode.core.urgencyOf
import dev.showcode.ui.rememberTickingNow
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.SurfaceInset
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.urgencyColor
import dev.showcode.ui.urgencyLabel

/**
 * 平铺总览（edge s4-onsite.boundary-overflow）。
 *
 * 双模哲学的一个必要例外：现场模式通常极低密度（单码占满屏），
 * 但该点 8 件时用户不想滑 8 次，需要中等密度一屏看完自己挑。
 * 承认这个例外，不要为了哲学一致性牺牲可用性。
 */
@Composable
fun TilesScreen(
    pointName: String,
    items: List<Parcel>,
    isDemo: Boolean,
    onBack: () -> Unit,
    onPick: (Parcel) -> Unit,
) {
    val tickingNow = rememberTickingNow(30_000L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
    ) {
        // 白底从 Column 提到 Box，水印才有地方放：放在内容底下才不压「申通快递」这类灰字，
        // 而 Column 若自带不透明白底，水印挪到第一个子节点就会被完全盖住。
        if (isDemo) DemoWatermark()

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = stringResource(R.string.onsite_tiles_title, pointName, items.size),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnsiteInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { parcel ->
                    Tile(
                        parcel = parcel,
                        urgency = urgencyOf(parcel, tickingNow),
                        onClick = { onPick(parcel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(parcel: Parcel, urgency: Urgency, onClick: () -> Unit) {
    val borderColor = if (urgency == Urgency.SAFE) Hairline else urgencyColor(urgency)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .background(if (urgency == Urgency.SAFE) SurfaceInset else OnsiteSurface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(
            text = parcel.code,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = TabularNumbers,
            color = OnsiteInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (parcel.lockerNo != null) {
                Text(
                    text = parcel.lockerNo,
                    modifier = Modifier
                        .background(SurfaceInset, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = OnsiteInk,
                )
            }
            if (urgency != Urgency.SAFE) {
                Text(
                    text = urgencyLabel(urgency),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = urgencyColor(urgency),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = parcel.carrier, fontSize = 11.sp, color = OnsiteInk.copy(alpha = 0.45f))
    }
}
