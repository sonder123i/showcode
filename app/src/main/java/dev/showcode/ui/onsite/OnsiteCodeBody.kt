package dev.showcode.ui.onsite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.Parcel
import dev.showcode.core.Urgency
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.urgencyColor

/**
 * 现场模式的核心渲染体。整个产品的灵魂就是这一块。
 *
 * 与 H5 参照 `showcode/src/flows/flow-1/flow1-onsite-present.tsx` 的 `OnsiteCodeBody` 1:1 对应，
 * 三条必须逐字复刻的规则：
 *
 * 1. **字号防溢出**：`min(base, 140/len vw)`，见 [codeFontSizeSp]。
 * 2. **有/无格口号的排版分叉**：柜号缺失时整块消失、取件码放大 15%——
 *    不是留白，是重排。绝不显示 null / "--" / 空白占位。
 * 3. **三态配色 + 倒计时**：安全态用墨色 50% 透明、临期与过期风险用各自的语义色；
 *    临期态的文案由调用方每秒重算，数字连续变化本身就是紧迫度的表达。
 */
@Composable
fun OnsiteCodeBody(
    parcel: Parcel,
    urgency: Urgency,
    lockerLabel: String,
    deadlineText: String?,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val hasLocker = parcel.lockerNo != null

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // vw 是视口宽度而不是内容宽度，所以必须在 padding 之前量：
        // 140 这个常数已经把两侧各 20 的内边距算进去了，再量一次内容宽就等于扣两遍边距。
        val viewportWidthDp = maxWidth.value
        val codeSize = codeFontSizeSp(parcel.code, hasLocker, viewportWidthDp, fontScale).sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 快递公司：最低视觉权重，只是让人确认「是这个包裹」
            Text(text = parcel.carrier, fontSize = 15.sp, color = OnsiteInk.copy(alpha = 0.55f))

            Spacer(Modifier.height(16.dp))

            // 取件码：等宽 + 超大 + 纯黑，为户外强光优化。长按复制（在位者 issue #52 的诉求）。
            Text(
                text = parcel.code,
                modifier = Modifier.pointerInput(parcel.code) {
                    detectTapGestures(onLongPress = { onCopyCode() })
                },
                fontSize = codeSize,
                lineHeight = codeSize * 1.05f,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.02.em,
                color = OnsiteInk,
                fontFamily = FontFamily.Monospace,
                style = TabularNumbers,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onsite_copy_hint),
                fontSize = 12.sp,
                color = OnsiteInk.copy(alpha = 0.55f),
            )

            // 柜号紧贴码下方。缺失时整块消失、码变大——不留空位、不显示 "--"
            if (hasLocker) {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .border(2.dp, OnsiteInk, RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = lockerLabel,
                        modifier = Modifier.alignByBaseline(),
                        fontSize = 14.sp,
                        color = OnsiteInk.copy(alpha = 0.6f),
                    )
                    Text(
                        text = parcel.lockerNo.orEmpty(),
                        modifier = Modifier.alignByBaseline(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularNumbers,
                        color = OnsiteInk,
                    )
                }
            }

            // 时效：三态下唯一的颜色。小字、低位，不抢码的注意力
            if (deadlineText != null) {
                val tint = if (urgency == Urgency.SAFE) OnsiteInk.copy(alpha = 0.5f) else urgencyColor(urgency)
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ClockIcon(color = tint, size = 16.dp)
                    Text(
                        text = deadlineText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        style = TabularNumbers,
                    )
                }
            }
        }
    }
}

/**
 * 时钟图标。material-icons-core 里没有 Schedule（那在 extended 包里），
 * 为一个 16dp 的装饰图标拖进整个 extended 依赖不值当，所以直接画。
 */
@Composable
private fun ClockIcon(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension / 12f
        val radius = this.size.minDimension / 2f - stroke / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
        drawLine(color, center, Offset(center.x, center.y - radius * 0.6f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(center.x + radius * 0.45f, center.y + radius * 0.2f), stroke, StrokeCap.Round)
    }
}
