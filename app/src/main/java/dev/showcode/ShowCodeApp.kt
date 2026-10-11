package dev.showcode

import android.app.Application
import android.content.Context
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
    }
}

val Context.app: ShowCodeApp get() = applicationContext as ShowCodeApp
