package dev.showcode.core.qr

/**
 * 把模块矩阵铺成**像素图**——每格一个模块，四周留静区。
 *
 * 存在的理由只有一个：让「画出来的到底是不是那张码」这件事能在 JVM 里被问出来。
 * 屏上那张是矢量画的（`ui/identity/QrMatrixCanvas`），但它和这里共用同一套算术：
 * `sizeWithQuietZone` 定总格数、`modulePx` 定每格边长、第 (x, y) 格落在
 * `((x + QUIET) * modulePx, (y + QUIET) * modulePx)`。
 * 不抽出这一层，「上下颠倒 / 深浅反色 / 静区漏了」这三类错**只有站在柜机前才会发现**。
 *
 * ⚠️ 这里**不含**画布上那 0.5px 的描边——那是防止相邻模块之间留出一条白缝的画法，
 * 不是码的内容。所以这一层证明的是映射对不对，不证明屏上有没有缝（那条仍然只能真机验）。
 */
data class QrRaster(
    /** 一个模块的边长（像素）。 */
    val modulePx: Int,
    /** 每边多少个模块，等于 `矩阵边长 + 静区 × 2`。 */
    val modules: Int,
    val dark: BooleanArray,
) {
    val widthPx: Int get() = modules * modulePx

    /** 静区占掉的像素数。外圈这一圈必须是白的。 */
    val quietPx: Int get() = QUIET_ZONE_MODULES * modulePx

    fun isDarkPixel(x: Int, y: Int): Boolean = dark[y * widthPx + x]

    /** 某个模块的中心像素。往返测试从像素反推矩阵就靠它。 */
    fun isDarkModule(mx: Int, my: Int): Boolean {
        val c = quietPx + mx * modulePx + modulePx / 2
        val r = quietPx + my * modulePx + modulePx / 2
        return isDarkPixel(c, r)
    }
}

/**
 * 铺成每格 [modulePx] 像素的图。
 *
 * 小于 1 直接拒而不是退化成一张「什么都没有」的图：传进来的通常是可用边长除出来再取整的数，
 * 取整成 0 恰恰发生在「这块屏太小、码画不下」那一刻，宁可调用方当场发现，
 * 也不要画出一张看起来像码而其实每格都是零像素的东西。
 */
fun QrMatrix.toRaster(modulePx: Int): QrRaster {
    require(modulePx >= 1) { "模块边长至少 1 像素，收到的是 $modulePx" }
    val modules = size + QUIET_ZONE_MODULES * 2
    val width = modules * modulePx
    val dark = BooleanArray(width * width)
    for (my in 0 until size) {
        val row = rows[my]
        for (mx in 0 until size) {
            if (!row[mx]) continue
            val x0 = (mx + QUIET_ZONE_MODULES) * modulePx
            val y0 = (my + QUIET_ZONE_MODULES) * modulePx
            for (dy in 0 until modulePx) {
                val base = (y0 + dy) * width
                for (dx in 0 until modulePx) dark[base + x0 + dx] = true
            }
        }
    }
    return QrRaster(modulePx = modulePx, modules = modules, dark = dark)
}

/**
 * 可用边长 [availablePx] 里放得下的最大模块边长。
 *
 * 画布和光栅都从这一格取尺寸：静区不是留白，**被挤掉一半，扫码器就判断不出码从哪里结束**，
 * 所以这里连静区一起除，而不是除完再加。
 */
fun QrMatrix.modulePx(availablePx: Float): Int =
    (availablePx / sizeWithQuietZone).toInt().coerceAtLeast(1)

/** 从像素图反推回模块矩阵（只取中心点采样）。往返测试用它证明「读回来的就是画出去的」。 */
fun QrRaster.toModules(): List<List<Boolean>> =
    List(size) { my -> List(size) { mx -> isDarkModule(mx, my) } }

private val QrRaster.size: Int get() = modules - QUIET_ZONE_MODULES * 2
