package dev.showcode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 指示胶囊的位置/按压/缩放状态机。移植自教务 `glass/DampedDragAnimation`（只砍了
 * `modifier` 那条内置拖拽入口——底栏用自己的 `pointerInput` 驱动本类，见 `ShowCodeNavBar`）。
 *
 * 分工是这个类存在的理由：**手指拥有位置，弹簧只管松手之后和点按**。
 * - `press()`：按下，三只弹簧并行往「按压态」走（进度 + 两轴缩放）；
 * - `updateValue()`：拖动，位置直接听手指，VelocityTracker 记录速度；
 * - `animateToValue()` / `release()`：松手，位置弹簧**带初速**落位（甩过去的手感来自这里），
 *   并在「滑过大半程」后才开始褪按压光（[awaitReleaseGate]）——不然全亮白环会拖出长残影。
 *
 * 弹簧参数取教务原文：按压进度 0.85/360、缩放 X 0.6/250、Y 0.7/250（按下阶段要贴手不抖）、
 * 落位/回弹由调用方给（底栏用 MotionTokens.NavSettle / NavRelease）。
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    private val visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val settleAnimationSpec: AnimationSpec<Float> = spring(0.5f, 340f, visibilityThreshold),
    private val releaseScaleAnimationSpec: AnimationSpec<Float> = spring(0.34f, 280f, 0.001f),
) {
    private val pressProgressAnimationSpec = spring(0.85f, 360f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val velocityTracker = VelocityTracker()
    private var interactionJob: Job? = null
    private var valueJob: Job? = null
    private var requestedValue by mutableFloatStateOf(initialValue)
    private var dragging by mutableStateOf(false)
    private var dragValue by mutableFloatStateOf(initialValue)
    private var dragVelocity by mutableFloatStateOf(0f)
    private var reducedMotion = false

    val value: Float get() = when {
        reducedMotion -> requestedValue
        dragging -> dragValue
        else -> valueAnimation.value
    }
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = if (reducedMotion) 0f else pressProgressAnimation.value
    val scaleX: Float get() = if (reducedMotion) initialScale else scaleXAnimation.value
    val scaleY: Float get() = if (reducedMotion) initialScale else scaleYAnimation.value
    val velocity: Float get() =
        positionVelocity / (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
    val positionVelocity: Float get() = when {
        reducedMotion -> 0f
        dragging -> dragVelocity
        else -> valueAnimation.velocity
    }

    fun setReducedMotion(reduced: Boolean, selectedValue: Float = targetValue) {
        reducedMotion = reduced
        if (reduced) snapToValue(selectedValue)
    }

    private fun snapToValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        interactionJob?.cancel()
        valueJob?.cancel()
        interactionJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(requestedValue)
            dragging = false
            dragVelocity = 0f
            pressProgressAnimation.snapTo(0f)
            scaleXAnimation.snapTo(initialScale)
            scaleYAnimation.snapTo(initialScale)
        }
    }

    fun press(uptimeMillis: Long = SystemClock.uptimeMillis()) {
        val current = value
        val currentVelocity = positionVelocity
        interactionJob?.cancel()
        valueJob?.cancel()
        dragValue = current
        dragVelocity = currentVelocity
        dragging = true
        velocityTracker.resetTracking()
        velocityTracker.addPosition(uptimeMillis, Offset(current, 0f))
        if (reducedMotion) return
        interactionJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        settleTo(targetValue, animatePress = false)
    }

    /** 手指拥有位置。弹簧只在松手后或点按时才接管。 */
    fun updateValue(
        value: Float,
        uptimeMillis: Long = SystemClock.uptimeMillis(),
    ) {
        val target = value.coerceIn(valueRange)
        requestedValue = target
        dragValue = target
        dragging = true
        velocityTracker.addPosition(uptimeMillis, Offset(target, 0f))
        dragVelocity = velocityTracker.calculateVelocity().x
    }

    fun animateToValue(value: Float) {
        settleTo(value, animatePress = true)
    }

    private fun settleTo(value: Float, animatePress: Boolean) {
        val target = value.coerceIn(valueRange)
        val current = this.value
        val currentVelocity = positionVelocity
        requestedValue = target
        if (reducedMotion) {
            snapToValue(target)
            return
        }
        interactionJob?.cancel()
        valueJob?.cancel()
        interactionJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // 先把弹簧停在最后画过的那一帧，再交回所有权——否则会跳。
            valueAnimation.snapTo(current)
            dragging = false
            coroutineScope {
                if (animatePress) {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
                    launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
                }
                valueJob = launch {
                    valueAnimation.animateTo(target, settleAnimationSpec, initialVelocity = currentVelocity)
                }
                launch {
                    awaitReleaseGate()
                    startReleaseAnimations(this)
                }
            }
        }
    }

    /** 滑动过大半程即开始褪光缩小，避免全亮白环拖出长残影（教务原注释）。 */
    private suspend fun awaitReleaseGate() {
        withFrameNanos { }
        if (value != targetValue) {
            val threshold =
                maxOf(visibilityThreshold, (valueRange.endInclusive - valueRange.start) * 0.15f)
            snapshotFlow { valueAnimation.value }
                .filter { abs(it - targetValue) <= threshold }
                .first()
        }
    }

    private fun startReleaseAnimations(scope: CoroutineScope) {
        scope.launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
        scope.launch { scaleXAnimation.animateTo(initialScale, releaseScaleAnimationSpec) }
        scope.launch { scaleYAnimation.animateTo(initialScale, releaseScaleAnimationSpec) }
    }
}
