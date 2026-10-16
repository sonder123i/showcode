package dev.showcode.ui.glass

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import dev.showcode.data.Prefs

/**
 * 玻璃的兼容性由**真实运行结果**决定，不按设备品牌或版本预测性屏蔽。
 *
 * 判据抄教务 `ui/system/GlassRuntimeGuard.kt`：读**系统自己的**进程退出原因
 * （`ActivityManager.getHistoricalProcessExitReasons`，API 30+），并且要求上一个进程当时确实
 * 开着玻璃（会话记号）——两个条件同时成立才算一次「与玻璃有关的上次失败」。
 *
 * ⚠️ 与家族当前立场一致：**只记日志取证，不自动熔断**。教务那一行注释是「实验期开关：不再因
 * 历史崩溃熔断降级…后续可恢复为熔断策略」。我 2026-10-15 自己发明过一条「记号没擦掉 = 崩过」
 * 的启发式并把玻璃自动关掉，实测会被**正常操作**误触发（`adb install -r`、设置里强行停止都不走
 * ON_STOP，下次冷启动就被判成失败）——同一轮里撞了三次。换成系统的退出原因之后，那类噪声根本
 * 不会命中：信号对了，才谈得上要不要动作。
 *
 * 已知代价：API 30 以下读不到退出原因（返回 false）。取件码 minSdk 26，所以 26~29 上这一层
 * 只剩会话记号，没有失败判定——和教务一样，那是平台能力的上限，不是漏做。
 */
object GlassRuntimeGuard {

    private const val TAG = "GlassRuntimeGuard"

    @Volatile
    private var sessionMarked = false

    @Volatile
    private var appContext: Context? = null

    /**
     * 冷启动时调一次（`ShowCodeApp.onCreate`，要早于任何一次渲染）。
     * 与家族那版一样，它只**记录**上一次像不像失败，然后擦掉记号，不关玻璃。
     */
    fun initialize(context: Context, prefs: Prefs) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        sessionMarked = false

        if (prefs.glassSessionUsed && didPreviousProcessFail(applicationContext)) {
            Log.w(TAG, "上一个进程看着像崩在玻璃渲染里；本轮仍启用玻璃，只留证据")
        }
        prefs.glassSessionUsed = false
    }

    /**
     * 顺序不可改：先看平台下限，再留会话记号。
     * 记号是给**下一次冷启动**读的，所以只在进程内第一次调用时写一次 prefs。
     */
    fun isBackdropEnabled(prefs: Prefs): Boolean {
        if (Build.VERSION.SDK_INT < GlassCompat.MIN_BACKDROP_SDK) return false
        markGlassSession(prefs)
        return true
    }

    private fun markGlassSession(prefs: Prefs) {
        if (sessionMarked) return
        synchronized(this) {
            if (sessionMarked) return
            sessionMarked = true
            prefs.glassSessionUsed = true
        }
    }

    private fun didPreviousProcessFail(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            val previousExit = context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(null, 0, 1)
                ?.firstOrNull()
                ?: return@runCatching false
            previousExit.reason in failedExitReasons
        }.getOrElse { error ->
            Log.w(TAG, "读不到上一个进程的退出原因", error)
            false
        }
    }

    private val failedExitReasons = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
    )
}