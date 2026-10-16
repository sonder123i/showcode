package dev.showcode.ui

import kotlin.math.abs
import kotlin.math.sign

/**
 * 滚动意图：底栏要不要收起来。逐字移植教务 `ui/system/NavScrollIntent.kt`（连阈值一起）。
 *
 * 两个阈值**刻意不对称**：向下滚（读内容）要攒够 40dp 才收，向上滚只要 20dp 就放回来。
 * 也就是「**收得难、放得容易**」——误收一次底栏比晚收一会儿更烦人。
 * 换方向时把累计量清零，所以来回抖不会累积。
 *
 * ⚠️ 符号约定（上一版移植时整个翻过一次，收起从未触发）：`deltaDp` 就是 Compose 的
 * 滚动增量（调用点传 `consumed.y / density`），**负 = 往下读**——往下读攒够 40dp 收，
 * 往上回读 20dp 就放。教务原文是 `travel < 0f` 判收起，这里逐字保持。
 *
 * 它只回答"意图变了没有"（返回 null 表示还没到阈值），不持有动画状态：
 * 收起/展开长什么样由调用方决定。
 */
class NavScrollIntent {
    private var travel = 0f

    fun reset() {
        travel = 0f
    }

    /** @param deltaDp 本次滚动增量（Compose 符号：**负 = 往下读**）。 */
    fun scroll(deltaDp: Float): Boolean? {
        if (!deltaDp.isFinite() || deltaDp == 0f) return null
        if (travel.sign != deltaDp.sign) travel = 0f
        travel += deltaDp
        val threshold = if (travel < 0f) 40f else 20f
        if (abs(travel) < threshold) return null
        val minimized = travel < 0f
        travel = 0f
        return minimized
    }
}