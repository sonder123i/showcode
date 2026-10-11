package dev.showcode.widget

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 组件点击 → App 内路由。
 * 走 StateFlow 而不是在 Activity 里直读 intent：MainActivity 是 singleTask，
 * 组件点击可能走 onNewIntent，两条路都要能落到同一个导航状态上。
 */
object WidgetNav {

    const val EXTRA_ROUTE = "sc_route"
    const val EXTRA_POINT_KEY = "sc_point_key"

    const val ROUTE_HOME = "home"
    const val ROUTE_ONSITE = "onsite"
    const val ROUTE_DEMO = "demo"
    const val ROUTE_UNRECOGNIZED = "unrecognized"

    val request = MutableStateFlow<Intent?>(null)

    fun routeOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ROUTE)

    fun pointKeyOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_POINT_KEY)
}
