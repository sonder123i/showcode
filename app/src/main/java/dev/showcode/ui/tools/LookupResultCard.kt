package dev.showcode.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.core.ParcelAction
import dev.showcode.core.ParsedParcel
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.PickupCodeParser
import dev.showcode.data.ShowCodeDb

/**
 * 查码结果卡片：识别成功后显示取件码 + 一键确认/跳转。
 *
 * 用户粘了一条快递单号过来，这里负责把解析结果画出来——
 * 认出来了就点一下跳到首页那格里去，没认出来就留白让用户去未识别页补规则。
 */
@Composable
fun LookupResultCard(
    message: IncomingMessage,
    enabledRules: List<dev.showcode.core.ParseRule>,
    onJumpToParcel: (ParsedParcel) -> Unit,
    onReject: (ParseOutcome.Rejected) -> Unit,
) {
    val outcome = PickupCodeParser.parse(message, enabledRules)

    when (outcome) {
        is ParseOutcome.Parsed -> {
            outcome.parcels.forEach { parcel ->
                ParcelResultCard(
                    parcel = parcel,
                    onJump = onJumpToParcel,
                )
            }
        }
        is ParseOutcome.Rejected -> onReject(outcome)
        else -> {
            Text("无法识别这条快递单号")
        }
    }
}

@Composable
private fun ParcelResultCard(
    parcel: ParsedParcel,
    onJump: (ParsedParcel) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .width(300.dp)
            .height(IntrinsicSize.Min),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { onJump(parcel) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = parcel.code,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = parcel.carrier,
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = parcel.pointName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            if (parcel.lockerNo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "格口：${parcel.lockerNo}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "点击跳转到对应取件点",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
    }
}
