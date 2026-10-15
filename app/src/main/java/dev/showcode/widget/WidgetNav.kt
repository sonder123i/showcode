package dev.showcode.widget

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 外部入口 → App 内路由。走这条路的有桌面组件、也有巡检提醒那条通知。
 * 用 StateFlow 而不是在 Activity 里直读 intent：MainActivity 是 singleTask，
 * 外部点击可能走 onNewIntent，两条路都要能落到同一个导航状态上。
 */
object WidgetNav {

    const val EXTRA_ROUTE = "sc_route"
    const val EXTRA_POINT_KEY = "sc_point_key"

    const val ROUTE_HOME = "home"
    const val ROUTE_ONSITE = "onsite"
    const val ROUTE_DEMO = "demo"
    const val ROUTE_UNRECOGNIZED = "unrecognized"

    /** 身份码那一格：预置的「去取」住在那儿，巡检提醒点开来就该落在这里。 */
    const val ROUTE_IDENTITY = "identity"

    val request = MutableStateFlow<Intent?>(null)

    fun routeOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ROUTE)

    fun pointKeyOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_POINT_KEY)
}
