package dev.showcode.core.qr

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 像素光栅与 PNG 往返。
 *
 * 这一层存在的意义是把清单里 C1 那句「这张码真扫得出来」**拆成两半**：
 * 柜机那颗摄像头拍不拍得清，只能设备验；而**画出去的到底是不是那张矩阵**不用设备——
 * 写一张真 PNG、再读回来逐格比对就能证明。上下颠倒、深浅反色、静区漏了这三类错
 * 从此在 CI 里就会被抓住，而不是等他站在机器前才发现。
 */
class QrRasterTest {

    private fun matrix(value: String): QrMatrix =
        Qr.of(value) ?: error("样本本身就编不出码：$value")

    /** 画成 PNG 再读回来。走真实的编码/解码，不留一条「自己写自己读」的捷径。 */
    private fun pngRoundTrip(raster: QrRaster): BufferedImage {
        val image = BufferedImage(raster.widthPx, raster.widthPx, BufferedImage.TYPE_BYTE_GRAY)
        for (y in 0 until raster.widthPx) {
            for (x in 0 until raster.widthPx) {
                // 深色模块 = 黑(0)，浅色 = 白(255)。反色码绝大多数扫码器读不出，
                // 所以这一格的约定就是测试结果的一部分，不是实现细节。
                val gray = if (raster.isDarkPixel(x, y)) 0 else 255
                val rgb = gray shl 16 or gray shl 8 or gray
                image.setRGB(x, y, rgb)
            }
        }
        val bytes = ByteArrayOutputStream()
        assertTrue("PNG 写不出来", ImageIO.write(image, "png", bytes))
        return ImageIO.read(ByteArrayInputStream(bytes.toByteArray()))
    }

    /**
     * 从图上取第 (mx, my) 个模块是不是深色。
     *
     * ⚠️ 位置是**在这里独立算出来的**，不调用生产代码里的那套映射：
     * 如果两边共用同一个错，比对就会自洽地通过，那种测试比没有测试更糟。
     */
    private fun darkOnImage(image: BufferedImage, raster: QrRaster, mx: Int, my: Int): Boolean {
        val x = raster.quietPx + mx * raster.modulePx + raster.modulePx / 2
        val y = raster.quietPx + my * raster.modulePx + raster.modulePx / 2
        return (image.getRGB(x, y) and 0xFF) < 128
    }

    @Test
    fun `PNG 往返：读回来的矩阵和画出去的一格不差`() {
        val qr = matrix("5-2-7712")
        val raster = qr.toRaster(modulePx = 4)
        val image = pngRoundTrip(raster)
        assertEquals(qr.size, raster.size)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                assertEquals(
                    "第 ($x,$y) 格在往返后变了",
                    qr.rows[y][x],
                    darkOnImage(image, raster, x, y),
                )
            }
        }
    }

    @Test
    fun `样本本身必须不对称，否则上面那条抓不到上下颠倒`() {
        // 转置是这一层最危险的错，而 QR 的定位角恰好对称——只有矩阵自身不对称才测得出。
        val qr = matrix("5-2-7712")
        val asymmetric = (0 until qr.size).any { y ->
            (0 until qr.size).any { x -> qr.rows[y][x] != qr.rows[x][y] }
        }
        assertTrue("样本矩阵上下对称，这条测试等于没跑", asymmetric)
    }

    @Test
    fun `静区那一圈在图上必须是白的`() {
        val raster = matrix("8-9-0765").toRaster(modulePx = 3)
        val image = pngRoundTrip(raster)
        assertEquals(QUIET_ZONE_MODULES * 3, raster.quietPx)
        for (i in 0 until raster.quietPx) {
            for (p in 0 until raster.widthPx) {
                assertTrue("上边距第 $i 行有深色", (image.getRGB(p, i) and 0xFF) > 128)
                assertTrue("左边距第 $i 列有深色", (image.getRGB(i, p) and 0xFF) > 128)
            }
        }
    }

    @Test
    fun `每格一个像素也得往返得动`() {
        // 这是「屏太小、码画不下」那一档的极限：中心点取样落在格子的左上角，
        // 一旦 mapping 差一格，这里就红。
        val qr = matrix("1234")
        val raster = qr.toRaster(modulePx = 1)
        val image = pngRoundTrip(raster)
        assertEquals(raster.widthPx, qr.size + QUIET_ZONE_MODULES * 2)
        assertEquals(raster.toModules(), qr.rows)
    }

    @Test
    fun `可用边长不够时不许塌成零像素一格`() {
        // modulePx 是画布唯一取的尺寸：它取整成 0 的那天，画出来的是一张「看着像码」的空白。
        assertEquals(1, matrix("5-2-7712").modulePx(availablePx = 10f))
        assertNotEquals(0, matrix("5-2-7712").modulePx(availablePx = 0.5f))
    }

    @Test
    fun `零像素的格子直接拒，不画一张看着像码的东西`() {
        var thrown = false
        try {
            matrix("5-2-7712").toRaster(modulePx = 0)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("toRaster 必须拒掉 0 边长", thrown)
    }

    /** 光栅的边长就是矩阵的边长（不含静区），往返两边都靠它对齐。 */
    private val QrRaster.size: Int get() = modules - QUIET_ZONE_MODULES * 2
}
