package dev.showcode.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.showcode.core.qr.QrMatrix
import dev.showcode.core.qr.QUIET_ZONE_MODULES
import dev.showcode.core.qr.modulePx
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.OnsiteSurface

/**
 * 把模块矩阵画成一张二维码。
 *
 * 用 `Canvas` 画矩形而不是先出 `Bitmap`：这一屏的尺寸由「现场那块屏」决定，
 * 位图要选一个固定分辨率，选小了柜机那个摄像头拍到马赛克，选大了白占内存。
 * 矢量画一遍还有个副产品——缩放时不会糊。
 *
 * 底色必须是浅色、模块必须是深色：**反色的码绝大多数扫码器读不出**，
 * 而这在手机上看起来只是「一张深底的码」，很好看。
 */
@Composable
fun QrMatrixCanvas(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // 尺寸算术只有**一份**：这里和 core/qr 的像素光栅共用 modulePx，
        // 于是 JVM 里那张 PNG 往返证明的映射，就是这块屏上真正画出去的映射。
        val side = minOf(size.width, size.height)
        val unit = matrix.modulePx(side).toFloat()
        // 向下取整会剩下不到一格的余量。画布从左上角起画，余量不平均分就会让码看着是歪的
        // ——静区因此一边比另一边宽，而它本来正是扫码器判断「码从哪里结束」的依据。
        val pad = unit * QUIET_ZONE_MODULES + (side - unit * matrix.sizeWithQuietZone) / 2f
        drawRect(color = OnsiteSurface, size = Size(size.width, size.height))
        // 每个模块多画一点点边：整数对齐时相邻模块之间会留出一条半透明的缝，
        // 那条缝在屏上看不见，在扫码器眼里是一根切断码图的白线。
        val bleed = unit + 0.5f
        matrix.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) {
                    drawRect(color = OnsiteInk, topLeft = Offset(pad + x * unit, pad + y * unit), size = Size(bleed, bleed))
                }
            }
        }
    }
}
