package dev.showcode.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 重启 / 升级之后重新安排组件的唤醒。
 *
 * `AlarmManager` 的闹钟不跨重启。以前重启之后 60 秒 tick 和跨界唤醒全部丢失，
 * 而没有任何东西会补挂——组件从此只在系统每 30 分钟的周期更新时动一下，
 * 丰巢那 18 小时倒计时会永远停在重启前的某一分钟上。
 *
 * 这里只重排闹钟和重绘组件，不启动 Activity：Android 10 以后后台也做不了后者。
 */
class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        PickupWidgetProvider.updateAll(context)
    }
}
