package dev.showcode.glass

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.showcode.ui.glass.GlassLensEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 折射引擎的 GL 冒烟探针：EGL 上下文 + 着色器程序能不能在这台设备的 GL 栈上起来。
 *
 * 引擎只在 API 31/32 被底栏消费（33+ 走库 lens()），但管线本体是纯 GLES20——
 * 这个探针在任何版本上都跑同一个 [GlassLensEngine.ensureReady]，验的是
 * 「搬进来的这套离屏 GL 在这台机器的驱动上不是坏的」，不是「底栏今天用没用它」。
 *
 * 教务同款思路（GlesRefractionProbeTest）：失败一次整体停用的判据必须先能被证伪。
 */
@RunWith(AndroidJUnit4::class)
class GlesLensProbeTest {

    @Test
    fun engineBootsOnThisDevice() {
        val latch = CountDownLatch(1)
        var ready = false
        GlassLensEngine.post {
            ready = GlassLensEngine.ensureReady()
            latch.countDown()
        }
        assertTrue("GL 线程 10s 没回话", latch.await(10, TimeUnit.SECONDS))
        assertTrue("EGL/着色器在这台设备上起不来——折射引擎不可用（查 logcat GlassLensEngine）", ready)
        assertTrue("着色器程序没建成", GlassLensEngine.program != 0)
    }
}
