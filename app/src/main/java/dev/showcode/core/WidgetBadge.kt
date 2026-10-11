package dev.showcode.core

/**
 * 桌面组件的未识别角标归属（frame 锁定规则，方案丙的连带约定）。
 *
 * 未识别角标是「静默失败的债务」的唯一常驻显性化入口，但它必须只出现一次：
 * 用户加了 3 个绑定组件再叠一个通用组件时，同一个数字重复 4 遍会让他以为有 4 批东西要看。
 * 所以规则是：**通用组件独占角标**；若一个通用组件都没有（只剩绑定组件），
 * 则由 id 最小的那个顶上，保证角标不会彻底消失。
 *
 * 抽成纯函数是因为模拟器无法注入短信，`unrecognized` 表在真机核对里造不出数据——
 * 这条规则否则连一条测试都不会有。
 */
fun badgeOwnerId(widgetIds: List<Int>, boundWidgetIds: Set<Int>): Int? {
    val ordered = widgetIds.sorted()
    return ordered.firstOrNull { it !in boundWidgetIds } ?: ordered.minOrNull()
}
