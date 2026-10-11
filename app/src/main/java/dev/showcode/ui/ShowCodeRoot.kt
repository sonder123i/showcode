package dev.showcode.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.showcode.app
import dev.showcode.core.DAY_MS
import dev.showcode.core.HealthIssue
import dev.showcode.core.groupByPoint
import dev.showcode.data.DemoData
import dev.showcode.ui.home.HomeEmpty
import dev.showcode.ui.home.HomeScreen
import dev.showcode.ui.home.HomeUiState
import dev.showcode.ui.inbox.ManualImportScreen
import dev.showcode.ui.inbox.UnrecognizedScreen
import dev.showcode.ui.onsite.OnsiteScreen
import dev.showcode.ui.onsite.TilesScreen
import dev.showcode.ui.onboarding.OnboardStep
import dev.showcode.ui.onboarding.OnboardingScreen
import dev.showcode.ui.settings.SettingsScreen
import dev.showcode.widget.PickupWidgetProvider
import dev.showcode.widget.WidgetNav

private enum class Route { HOME, ONSITE, TILES, SETTINGS, UNRECOGNIZED, MANUAL_IMPORT, ONBOARDING }

/** 首页「最近已取」最多列几条。它是误触后的补救出口，不是主路径，不该占满一屏。 */
private const val RECENTLY_PICKED_LIMIT = 5

/**
 * 三个主屏 + 三个次屏，用一个枚举做路由。
 * 不引 navigation-compose：缓存里只有 2.7.7，与 Compose 1.12 不兼容，
 * 而这个规模的状态机比一张导航图更好读。
 */
@Composable
fun ShowCodeRoot() {
    val context = LocalContext.current
    val repository = context.app.repository
    val appData by repository.data.collectAsState()

    var route by rememberSaveable {
        mutableStateOf(if (repository.prefs.onboardingDone) Route.HOME else Route.ONBOARDING)
    }
    var pointKey by rememberSaveable { mutableStateOf("") }
    // 点名要在导航时存下来：该点的件全取完之后分组会消失，
    // 而现场模式的空态仍然要把「我来对地方了」这句话讲完整。
    var pointName by rememberSaveable { mutableStateOf("") }
    var focusParcelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var manualPrefill by rememberSaveable { mutableStateOf("") }
    var manualSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var manualReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    var onboardStep by rememberSaveable { mutableStateOf(OnboardStep.WELCOME) }
    var smsAttempted by rememberSaveable { mutableStateOf(false) }
    var widgetAdded by remember { mutableStateOf(false) }

    // 演示数据只在进入时生成一次，把截止时刻冻结住——之后倒计时才会真的往下走。
    // 「已取」只改这个集合，一个字都不落库，所以退出演示后不残留任何示例内容。
    val demoParcels = remember { DemoData.parcels(System.currentTimeMillis()) }
    val demoPicked = remember { mutableStateListOf<Long>() }

    val now = rememberTickingNow(30_000L)
    val thresholds by repository.thresholds.collectAsState()

    val realGroups = remember(appData.parcels, appData.aliases, now, thresholds) {
        groupByPoint(appData.parcels, now, thresholds, appData.aliases)
    }
    val sampleGroups = remember(demoParcels, now, thresholds) { groupByPoint(demoParcels, now, thresholds) }
    val demoGroups = remember(demoParcels, demoPicked.toList(), now, thresholds) {
        groupByPoint(demoParcels.filterNot { demoPicked.contains(it.id) }, now, thresholds)
    }
    val activeGroups = if (demo) demoGroups else realGroups

    var smsGranted by remember { mutableStateOf(repository.hasSmsPermission()) }
    var notificationGranted by remember { mutableStateOf(repository.hasNotificationAccess()) }

    // 通知监听权只能在系统设置里给，回来时必须重新读一次，否则按钮会一直挂着
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = repository.hasSmsPermission()
                notificationGranted = repository.hasNotificationAccess()
                widgetAdded = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, PickupWidgetProvider::class.java))
                    .isNotEmpty()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var advanceAfterSms by remember { mutableStateOf(false) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        smsGranted = result[Manifest.permission.READ_SMS] == true
        smsAttempted = true
        // 授权后立刻回填收件箱：用户手机上本来就躺着待取件，不该让他再等下一条短信
        if (smsGranted) repository.backfillSms()
        if (advanceAfterSms) {
            advanceAfterSms = false
            if (smsGranted) onboardStep = OnboardStep.NOTIFICATION
        }
    }

    fun requestSmsPermission(fromOnboarding: Boolean = false) {
        advanceAfterSms = fromOnboarding
        smsPermissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
    }

    fun openNotificationSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    fun openAppDetails() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        }
    }

    /** 方案丙的反向绑定：从 App 内请求再钉一个绑定单点的组件。 */
    fun requestPinWidget() {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(
                    ComponentName(context, PickupWidgetProvider::class.java),
                    null,
                    null,
                )
            }
        }
    }

    fun openPoint(key: String, name: String) {
        pointKey = key
        pointName = name
        focusParcelId = null
        route = Route.ONSITE
    }

    fun enterDemo(key: String) {
        demo = true
        demoPicked.clear()
        pointKey = key
        pointName = sampleGroups.firstOrNull { it.key == key }?.name.orEmpty()
        focusParcelId = null
        route = Route.ONSITE
    }

    /** 退出演示即清空演示痕迹；引导没走完时回到引导，而不是首页。 */
    fun exitDemo() {
        demo = false
        demoPicked.clear()
        if (repository.prefs.onboardingDone) {
            route = Route.HOME
        } else {
            onboardStep = OnboardStep.SMS
            route = Route.ONBOARDING
        }
    }

    // 组件点击的深链：冷启动与 onNewIntent 都汇到 WidgetNav.request
    LaunchedEffect(Unit) {
        WidgetNav.request.collect { intent ->
            val target = WidgetNav.routeOf(intent) ?: return@collect
            WidgetNav.request.value = null
            when (target) {
                WidgetNav.ROUTE_ONSITE -> {
                    val key = WidgetNav.pointKeyOf(intent).orEmpty()
                    demo = false
                    openPoint(key, realGroups.firstOrNull { it.key == key }?.name.orEmpty())
                }
                WidgetNav.ROUTE_DEMO -> enterDemo(sampleGroups.firstOrNull()?.key.orEmpty())
                WidgetNav.ROUTE_UNRECOGNIZED -> route = Route.UNRECOGNIZED
                else -> route = Route.HOME
            }
        }
    }

    val healthReport = remember(appData.lastSyncAt, smsGranted, notificationGranted, now) {
        repository.healthReport(now)
    }

    val homeState = HomeUiState(
        loaded = appData.loaded,
        groups = realGroups,
        empty = when {
            !appData.loaded -> HomeEmpty.NONE
            realGroups.isNotEmpty() -> HomeEmpty.NONE
            appData.monthlyPicked > 0 -> HomeEmpty.ALL_DONE
            else -> HomeEmpty.FIRST_TIME
        },
        monthlyPicked = appData.monthlyPicked,
        lastSyncAt = appData.lastSyncAt,
        unrecognizedCount = appData.unrecognized.size,
        recentlyPicked = remember(appData.parcels, now) {
            appData.parcels
                .filter { it.picked && (it.pickedAt ?: 0L) > now - DAY_MS }
                .sortedByDescending { it.pickedAt }
                .take(RECENTLY_PICKED_LIMIT)
        },
        showWidgetHint = !repository.prefs.widgetHintDismissed && realGroups.isNotEmpty(),
        smsPermissionGranted = smsGranted,
        notificationAccessGranted = notificationGranted,
    )

    when (route) {
        Route.HOME -> HomeScreen(
            state = homeState,
            now = now,
            sampleGroups = sampleGroups,
            onOpenPoint = { key -> openPoint(key, realGroups.firstOrNull { it.key == key }?.name.orEmpty()) },
            onOpenDemo = { key -> enterDemo(key) },
            onRestoreParcel = { parcel -> repository.undoPicked(parcel.id) },
            report = healthReport,
            onOpenSettings = { route = Route.SETTINGS },
            onFixWorst = {
                when (healthReport.worst) {
                    HealthIssue.SMS_DENIED -> requestSmsPermission()
                    HealthIssue.NOTIFICATION_DENIED -> openNotificationSettings()
                    else -> openAppDetails()
                }
            },
            onOpenUnrecognized = { route = Route.UNRECOGNIZED },
            onManualImport = {
                manualPrefill = ""
                manualSourceId = null
                manualReturn = Route.HOME
                route = Route.MANUAL_IMPORT
            },
            onDismissWidgetHint = { repository.prefs.widgetHintDismissed = true },
            onPinWidget = { requestPinWidget() },
            onRequestSmsPermission = { requestSmsPermission() },
            onOpenNotificationSettings = { openNotificationSettings() },
            onRefresh = { repository.refreshNow() },
        )

        Route.ONSITE -> {
            val group = activeGroups.firstOrNull { it.key == pointKey }
            OnsiteScreen(
                pointName = pointName,
                items = group?.items.orEmpty(),
                now = now,
                isDemo = demo,
                focusParcelId = focusParcelId,
                lastPickupAt = appData.lastPickupAt,
                allPickedHere = if (demo) {
                    demoParcels.any { it.pointKey == pointKey && demoPicked.contains(it.id) }
                } else {
                    appData.parcels.any { it.pointKey == pointKey && it.picked }
                },
                onBack = { if (demo) exitDemo() else route = Route.HOME },
                onExitDemo = { exitDemo() },
                onOpenTiles = { currentId ->
                    focusParcelId = currentId
                    route = Route.TILES
                },
                onMarkPicked = { parcel ->
                    if (demo) demoPicked.add(parcel.id) else repository.markPicked(parcel.id)
                },
                onUndoPicked = { parcel ->
                    if (demo) demoPicked.remove(parcel.id) else repository.undoPicked(parcel.id)
                },
                onRefresh = { if (!demo) repository.refreshNow() },
            )
        }

        Route.TILES -> {
            val group = activeGroups.firstOrNull { it.key == pointKey }
            TilesScreen(
                pointName = pointName,
                items = group?.items.orEmpty(),
                isDemo = demo,
                onBack = { route = Route.ONSITE },
                onPick = { parcel ->
                    focusParcelId = parcel.id
                    route = Route.ONSITE
                },
            )
        }

        Route.SETTINGS -> SettingsScreen(
            thresholds = thresholds,
            parcels = appData.parcels,
            now = now,
            report = healthReport,
            smsGranted = smsGranted,
            notificationEnabled = notificationGranted,
            unrecognizedCount = appData.unrecognized.size,
            onOpenUnrecognized = { route = Route.UNRECOGNIZED },
            onLockerHours = { repository.setLockerDueSoonHours(it) },
            onStationMinutes = { repository.setStationDueSoonMinutes(it) },
            onRestoreDefaults = { repository.restoreThresholdDefaults() },
            onRequestSms = { requestSmsPermission() },
            onOpenNotificationSettings = { openNotificationSettings() },
            onOpenAppDetails = { openAppDetails() },
            onBack = { route = Route.HOME },
        )

        Route.UNRECOGNIZED -> UnrecognizedScreen(
            messages = appData.unrecognized,
            now = now,
            onBack = { route = Route.HOME },
            onExtract = { message ->
                manualPrefill = message.body
                manualSourceId = message.id
                manualReturn = Route.UNRECOGNIZED
                route = Route.MANUAL_IMPORT
            },
            onDismiss = { message -> repository.clearUnrecognized(message.id) },
        )

        Route.MANUAL_IMPORT -> ManualImportScreen(
            prefillRaw = manualPrefill,
            onBack = { route = manualReturn },
            onSave = { code, name, locker, raw ->
                repository.manualAdd(code, name, locker, raw)
                manualSourceId?.let { repository.clearUnrecognized(it) }
                manualSourceId = null
                manualPrefill = ""
                // 从引导里手动导入成功 = 用户已经拿到价值，引导可以收尾了
                if (manualReturn == Route.ONBOARDING) repository.prefs.onboardingDone = true
                manualReturn = Route.HOME
                route = Route.HOME
            },
        )

        Route.ONBOARDING -> OnboardingScreen(
            step = onboardStep,
            report = healthReport,
            smsGranted = smsGranted,
            notificationEnabled = notificationGranted,
            widgetAdded = widgetAdded,
            smsAttempted = smsAttempted,
            onStep = { onboardStep = it },
            onEnterDemo = { enterDemo(sampleGroups.firstOrNull()?.key.orEmpty()) },
            onRequestSms = { requestSmsPermission(fromOnboarding = true) },
            onOpenNotificationSettings = { openNotificationSettings() },
            onPinWidget = { requestPinWidget() },
            onManualImport = {
                manualPrefill = ""
                manualSourceId = null
                manualReturn = Route.ONBOARDING
                route = Route.MANUAL_IMPORT
            },
            onFinish = {
                repository.prefs.onboardingDone = true
                onboardStep = OnboardStep.WELCOME
                route = Route.HOME
            },
        )
    }
}
