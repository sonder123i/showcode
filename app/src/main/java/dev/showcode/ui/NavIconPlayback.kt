package dev.showcode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 每格一只时钟，可见字形、收起胶囊共用。逐字移植教务 `NavigationIconPlayback`：
 *
 * - 一轮 480ms 线性播放，`phase()` 取小数部分，所以渲染器拿到的永远是 0..1；
 * - 未播完再触发时**从当前帧续播**，绝不跳回零（跳帧会看成闪一下）；
 * - 离场（换到别的格）160ms 收尾到整数帧，停在字形的静止姿态；
 * - reduceMotion 直接落位，不播。
 */
@Stable
class NavIconPlayback(count: Int, private val scope: CoroutineScope) {
    private val clocks = List(count) { Animatable(0f) }
    private val jobs = arrayOfNulls<Job>(count)
    private var selected = -1
    private var reduced = false

    fun phase(index: Int): Float = clocks[index].value.let { it - floor(it) }

    fun select(index: Int, reduceMotion: Boolean) {
        if (reduced != reduceMotion) {
            reduced = reduceMotion
            if (reduced) clocks.indices.forEach { settle(it, immediately = true) }
        }
        if (selected == index) return
        if (selected >= 0) settle(selected, immediately = reduced)
        selected = index
        replay(index)
    }

    fun replay(index: Int) {
        if (reduced) return
        val clock = clocks[index]
        jobs[index]?.cancel()
        // Retarget an unfinished cycle from its current frame; never snap back to zero.
        val end = floor(clock.value) + if (clock.value % 1f > 0.001f) 2f else 1f
        jobs[index] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            clock.animateTo(end, tween(((end - clock.value) * 480).toInt(), easing = LinearEasing))
        }
    }

    private fun settle(index: Int, immediately: Boolean) {
        jobs[index]?.cancel()
        val clock = clocks[index]
        jobs[index] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val end = ceil(clock.value)
            if (immediately) clock.snapTo(end) else clock.animateTo(end, tween(160))
        }
    }

    fun dispose() = jobs.forEach { it?.cancel() }
}

@Composable
fun rememberNavIconPlayback(count: Int, selected: Int, reduced: Boolean): NavIconPlayback {
    val scope = rememberCoroutineScope()
    val state = remember(count, scope) { NavIconPlayback(count, scope) }
    LaunchedEffect(state, selected, reduced) { state.select(selected, reduced) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}
