package dev.showcode

import android.app.Application
import android.content.Context
import dev.showcode.data.PickupReminder
import dev.showcode.data.ParcelRepository
import dev.showcode.data.ShowCodeDb
import dev.showcode.widget.PickupWidgetProvider

class ShowCodeApp : Application() {

    lateinit var repository: ParcelRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ParcelRepository(this, ShowCodeDb(this))
        // 组件是用户不打开 App 也会看到的表面：数据或阈值一变它就得跟着重绘
        repository.onDataChange = { PickupWidgetProvider.updateAll(this) }
        repository.start()
        // 渠道必须早于任何一次 notify：渠道没建好就发通知，在国产 ROM 上的症状是静默丢掉。
        PickupReminder.ensureChannel(this)
        // 每次启动重排一次。挂闹钟便宜且幂等，要不要响由 fire 那边判。
        PickupReminder.schedule(this)
    }
}

val Context.app: ShowCodeApp get() = applicationContext as ShowCodeApp
