package dev.showcode.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.showcode.R
import dev.showcode.app
import dev.showcode.core.TileAction
import dev.showcode.core.TileReadout
import dev.showcode.core.tileReadout
import dev.showcode.core.urgentCount

/**
 * 下拉栏那颗磁贴：**没给权限时它是出口，给了之后它是动作**。
 *
 * 刻意不做社区流传的那个「重写 `enabled_notification_listeners` 逼系统重绑」的自救技巧：
 * 那一步要 `WRITE_SECURE_SETTINGS`，普通安装下只会抛 SecurityException，
 * 而一个只在 adb 上能跑的按钮不该出现在下拉栏里。监听被 ROM 杀掉时，
 * 磁贴该做的是把红着的现状说出来（首页那条体检条已经负责这个），不是假装修好了它。
 *
 * 权限也**不在这里要**：磁贴没有申请权限的位置，能做的只有把那块系统设置页摊开。
 */
class PickupTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        when (readout().action) {
            TileAction.OPEN_LISTENER_SETTINGS -> openListenerSettings()
            // 刷新是异步的：点完不能就地假设件数已经变了，等它落库再重画一次。
            TileAction.REFRESH_NOW -> applicationContext.app.repository.refreshNow().invokeOnCompletion {
                main.post { render() }
            }
        }
    }

    private fun readout(): TileReadout {
        val parcels = applicationContext.app.repository.data.value.parcels
        return tileReadout(
            notificationGranted = NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(packageName),
            pending = parcels.count { !it.picked },
            urgent = urgentCount(parcels, System.currentTimeMillis()),
        )
    }

    private fun render() {
        val tile = qsTile ?: return
        val state = readout()
        tile.label = getString(
            if (state.action == TileAction.OPEN_LISTENER_SETTINGS) R.string.tile_off else R.string.tile_name,
        )
        // 副标题是 API 29 才有的：更低的机器上只剩一行标题，所以标题自己必须说得完。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !state.reportCounts -> getString(R.string.tile_sub_off)
                state.pending == 0 -> getString(R.string.tile_sub_none)
                state.urgent > 0 -> getString(R.string.tile_sub_urgent, state.pending, state.urgent)
                else -> getString(R.string.tile_sub_count, state.pending)
            }
        }
        // 没件也标 ACTIVE：灰掉的磁贴读起来像坏了，而它此刻说的是实话。
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private fun openListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34 起这条路径要求 PendingIntent；直接 startActivity 会让磁贴不收起面板，
                // 用户看到的是「点了一下，什么都没发生」。
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
