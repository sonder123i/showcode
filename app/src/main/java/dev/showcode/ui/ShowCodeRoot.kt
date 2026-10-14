package dev.showcode.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import dev.showcode.core.Owner
import dev.showcode.core.PointAlias
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.bodyFingerprint
import dev.showcode.core.groupByPoint
import dev.showcode.core.mergePreview
import dev.showcode.core.suspectDuplicates
import dev.showcode.ui.points.MergePreviewScreen
import dev.showcode.ui.points.PointDetailScreen
import kotlinx.coroutines.delay
import dev.showcode.data.DemoData
import dev.showcode.data.InstalledApp
import dev.showcode.data.unrecognizedBadgeCount
import dev.showcode.ui.home.HomeEmpty
import dev.showcode.ui.home.HomeScreen
import dev.showcode.ui.home.HomeUiState
import dev.showcode.ui.inbox.ManualImportScreen
import dev.showcode.ui.inbox.UnrecognizedScreen
import dev.showcode.ui.identity.IdentityEditScreen
import dev.showcode.ui.identity.IdentityListScreen
import dev.showcode.ui.onsite.IdentityShowScreen
import dev.showcode.ui.onsite.OnsiteScreen
import dev.showcode.ui.onsite.TilesScreen
import dev.showcode.ui.onboarding.OnboardStep
import dev.showcode.ui.onboarding.OnboardingScreen
import dev.showcode.ui.rules.RuleEditScreen
import dev.showcode.ui.rules.RuleListScreen
import dev.showcode.ui.settings.NotifyAppsScreen
import dev.showcode.ui.settings.SettingsScreen
import dev.showcode.widget.PickupWidgetProvider
import dev.showcode.widget.WidgetNav

private enum class Route {
    HOME, ONSITE, TILES, SETTINGS, UNRECOGNIZED, MANUAL_IMPORT, ONBOARDING, POINT_DETAIL,
    MERGE_PREVIEW, RULES, RULE_EDIT, IDENTITY_LIST, IDENTITY_ADD, IDENTITY_SHOW, NOTIFY_APPS,
}

/* 合并后撤销条的驻留时长。story-6 的 user_side 写的是「五秒内屏幕下方有个已合并 · 撤销」。 */
private const val MERGE_UNDO_MS = 5_000L

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
    var manualSourceBody by rememberSaveable { mutableStateOf<String?>(null) }
    var manualReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    var onboardStep by rememberSaveable { mutableStateOf(OnboardStep.WELCOME) }
    var detailKey by rememberSaveable { mutableStateOf("") }
    /* 合并预览与「刚合过一次」的撤销态。预览内容里有 List，存不进 Saveable，
       而进程在弹层开着时被杀掉是无关紧要的丢失——重开一次预览就行。 */
    var pendingMerge by remember { mutableStateOf<dev.showcode.core.MergePreview?>(null) }
    var mergeReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    var unrecognizedReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    /* 规则编辑器的原文预填。从没认出来的那条短信进来时带着它的正文——
       那一屏要治的就是眼前这一条，让用户再抄一遍原文等于没做这个入口。 */
    var rulePrefillRaw by rememberSaveable { mutableStateOf("") }
    var ruleReturn by rememberSaveable { mutableStateOf(Route.SETTINGS) }
    var identityReturn by rememberSaveable { mutableStateOf(Route.SETTINGS) }
    var notifyReturn by rememberSaveable { mutableStateOf(Route.SETTINGS) }
    /** 正在出示哪一张。存 id 不存对象：码被删掉时那一屏必须跟着退回去，而不是展示一张已经不存在的码。 */
    var identityShowId by rememberSaveable { mutableStateOf(0L) }
    /* 能启动的 App。只在进「存一张」那一屏时查一次：这个查询要对几百个包各解一次显示名，
       放在组合里每帧跑一遍是会卡的。 */
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    /* 代取件分组的名字。写在这里而不是每次现读 prefs：prefs 不是可观察的，
       用户在设置页改完必须当场反映到现场模式的那个分组标签上。 */
    var familyLabel by remember { mutableStateOf(repository.prefs.familyLabel) }
    var keepTarget by rememberSaveable { mutableStateOf(true) }
    var lastMerge by remember { mutableStateOf<PointAlias?>(null) }
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

    /* 轻提示只报字面重叠的一对，且用户说过「不是」之后就永久闭嘴。
       AC4 的红线是绝不自动合并，所以这里只产生一个候选、不产生任何数据变化。 */
    val suspect = remember(realGroups, appData.dismissals) {
        suspectDuplicates(realGroups, appData.dismissals).firstOrNull()
    }

    LaunchedEffect(lastMerge) {
        if (lastMerge != null) {
            delay(MERGE_UNDO_MS)
            lastMerge = null
        }
    }

    // 装了什么 App 这件事不会在本次会话里变，所以查一次就够；离开那一屏也不清，回来不用重查。
    LaunchedEffect(route) {
        if (route == Route.IDENTITY_ADD && installedApps.isEmpty()) {
            installedApps = repository.installedApps()
        }
    }

    fun openMergePreview(a: PickupPointGroup, b: PickupPointGroup, preferTarget: PickupPointGroup? = null) {
        /* 从详情页点「并过来」时方向已经由用户定死了——并到他正在看的那个点，
           这里不能再拿件数去翻他的决定。只有轻提示那条路径两边没有主客，
           才用件数默认保留谁的名字（件多是「这个点一直在用」的证据）。 */
        val (source, target) = when {
            preferTarget != null -> (if (preferTarget.key == a.key) b else a) to preferTarget
            b.items.size > a.items.size -> a to b
            else -> b to a
        }
        pendingMerge = mergePreview(source, target)
        keepTarget = true
        mergeReturn = route
        route = Route.MERGE_PREVIEW
    }

    fun confirmMerge() {
        val preview = pendingMerge ?: return
        val source = if (keepTarget) preview.source else preview.target
        val target = if (keepTarget) preview.target else preview.source
        val record = PointAlias(
            aliasKey = source.key,
            aliasName = source.name,
            targetKey = target.key,
            targetName = target.name,
            createdAt = System.currentTimeMillis(),
        )
        repository.mergePoints(record)
        lastMerge = record
        pendingMerge = null
        route = mergeReturn
        /* 详情页刚刚看着 A 的视角，合并完要跟着活下来的那个点，
           否则回去会看见一个已经不存在的分组。 */
        if (mergeReturn == Route.POINT_DETAIL) detailKey = target.key
    }

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

    /* 打开用户自己选过的那个官方 App。走系统的启动意图，**不走写死的深链**：
       竞品那种 `tbopen://…` 直达路径随对方版本变，而我们连对方版本都看不到——
       这一颗按钮唯一的职责是「码扫不过时把人送到能救他的地方」，送到首页就够了，
       送进一个 404 页面比不送更糟。 */
    fun openOfficialApp(packageName: String) {
        if (packageName.isBlank()) return
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull()
            ?.let { runCatching { context.startActivity(it) } }
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

    /* 系统返回键。批 1 起每个子屏都只画了自己的左上角箭头，没接 BackHandler，
       于是按返回 = 直接退出 App。站在柜机前被退到桌面，是比少显示一个码更让人恼火的事。
       引导的第一步例外：那时没有「来路」可回，返回就该退出。 */
    BackHandler(enabled = route != Route.HOME && !(route == Route.ONBOARDING && onboardStep == OnboardStep.WELCOME)) {
        when (route) {
            Route.ONSITE -> if (demo) exitDemo() else route = Route.HOME
            Route.TILES -> route = Route.ONSITE
            Route.POINT_DETAIL -> route = Route.HOME
            Route.MERGE_PREVIEW -> {
                pendingMerge = null
                route = mergeReturn
            }
            Route.SETTINGS -> route = Route.HOME
            Route.UNRECOGNIZED -> route = unrecognizedReturn
            Route.RULES -> route = ruleReturn
            // 编辑器写完只清预填，不清规则内容：正写着被返回键打断，回来还能接着写。
            Route.RULE_EDIT -> {
                rulePrefillRaw = ""
                route = Route.RULES
            }
            Route.IDENTITY_LIST -> route = identityReturn
            Route.IDENTITY_ADD -> route = Route.IDENTITY_LIST
            Route.IDENTITY_SHOW -> route = Route.IDENTITY_LIST
            Route.NOTIFY_APPS -> route = notifyReturn
            Route.MANUAL_IMPORT -> route = manualReturn
            Route.ONBOARDING -> {
                val steps = OnboardStep.entries
                onboardStep = steps[(steps.indexOf(onboardStep) - 1).coerceAtLeast(0)]
            }
            Route.HOME -> Unit
        }
    }

    val healthReport = remember(appData.lastSyncAt, smsGranted, notificationGranted, now) {
        repository.healthReport(now)
    }

    /* 剪贴板自动填的「这条你已经导过了」判据。两批都要：
       只取待取件的 raw 的话，用户从收容页那条复制原文回来会被再填一次——
       而那个列表存在的理由恰恰是「这条还没处理完」，不是「这条不存在」。
       remember 是必需的：指纹是 sha256，几十条正文每次重组重算一遍没有理由。 */
    val knownBodies = remember(appData.parcels, appData.unrecognized) {
        appData.parcels.mapTo(mutableSetOf()) { bodyFingerprint(it.raw) } +
            appData.unrecognized.map { bodyFingerprint(it.body) }
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
        syncing = appData.syncing,
        lastSyncResult = appData.lastSyncResult,
        lastSyncFailed = appData.lastSyncFailed,
        unrecognizedCount = unrecognizedBadgeCount(appData.unrecognized),
        /* 检索那句「你可能已经取过了」的依据。和下面的 recentlyPicked 不是一回事：
           那栏只留最近 5 件、一天以内（误触补救出口），而一个月前取走的那件也值得被认出来。 */
        pickedParcels = remember(appData.parcels) { appData.parcels.filter { it.picked } },
        recentlyPicked = remember(appData.parcels, now) {
            appData.parcels
                .filter { it.picked && (it.pickedAt ?: 0L) > now - DAY_MS }
                .sortedByDescending { it.pickedAt }
                .take(RECENTLY_PICKED_LIMIT)
        },
        showWidgetHint = !repository.prefs.widgetHintDismissed && realGroups.isNotEmpty(),
        smsPermissionGranted = smsGranted,
        notificationAccessGranted = notificationGranted,
        suspect = suspect,
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
                manualSourceBody = null
                manualReturn = Route.HOME
                route = Route.MANUAL_IMPORT
            },
            onDismissWidgetHint = { repository.prefs.widgetHintDismissed = true },
            onPinWidget = { requestPinWidget() },
            onRequestSmsPermission = { requestSmsPermission() },
            onOpenNotificationSettings = { openNotificationSettings() },
            onRefresh = { repository.refreshNow() },
            onManagePoint = { group ->
                detailKey = group.key
                route = Route.POINT_DETAIL
            },
            onSuspectYes = { suspect?.let { openMergePreview(it.first, it.second) } },
            onSuspectNo = { suspect?.let { repository.dismissSuspect(it.dismissKey) } },
            mergeUndoName = lastMerge?.targetName,
            onUndoMerge = {
                lastMerge?.let { repository.undoMerge(it.aliasKey) }
                lastMerge = null
            },
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
                familyLabel = familyLabel,
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
            unrecognizedCount = unrecognizedBadgeCount(appData.unrecognized),
            ruleCount = appData.rules.size,
            onOpenRules = {
                ruleReturn = Route.SETTINGS
                route = Route.RULES
            },
            identityCount = appData.identityCodes.size,
            onOpenIdentity = {
                identityReturn = Route.SETTINGS
                route = Route.IDENTITY_LIST
            },
            notifyCount = appData.notifyApps.size,
            onOpenNotify = {
                notifyReturn = Route.SETTINGS
                route = Route.NOTIFY_APPS
            },
            onOpenUnrecognized = {
                unrecognizedReturn = Route.SETTINGS
                route = Route.UNRECOGNIZED
            },
            familyLabel = familyLabel,
            onFamilyLabel = {
                repository.prefs.familyLabel = it
                familyLabel = repository.prefs.familyLabel
            },
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
            mutedSenders = appData.mutedSenders,
            now = now,
            onBack = { route = unrecognizedReturn },
            onExtract = { message ->
                manualPrefill = message.body
                manualSourceBody = message.body
                manualReturn = Route.UNRECOGNIZED
                route = Route.MANUAL_IMPORT
            },
            onDismiss = { message -> repository.dismissUnrecognized(message.id) },
            onMuteSender = { message -> repository.muteSender(message.sender) },
            onUnmuteSender = { key -> repository.unmuteSender(key) },
            onWriteRule = { message ->
                rulePrefillRaw = message.body
                ruleReturn = Route.UNRECOGNIZED
                route = Route.RULE_EDIT
            },
        )

        Route.RULES -> RuleListScreen(
            rules = appData.rules,
            onBack = { route = ruleReturn },
            onAdd = {
                rulePrefillRaw = ""
                route = Route.RULE_EDIT
            },
            onToggle = { id, enabled -> repository.setRuleEnabled(id, enabled) },
            onDelete = { id -> repository.deleteParseRule(id) },
        )

        Route.RULE_EDIT -> RuleEditScreen(
            prefillRaw = rulePrefillRaw,
            now = now,
            onBack = { route = Route.RULES },
            onCheck = { pattern, pointName, sample ->
                repository.checkRule(pattern, pointName, sample, appData.rules.size)
            },
            onSave = { pattern, pointName, lockerNo -> repository.addParseRule(pattern, pointName, lockerNo) },
        )

        Route.IDENTITY_LIST -> IdentityListScreen(
            codes = appData.identityCodes,
            onBack = { route = identityReturn },
            onAdd = { route = Route.IDENTITY_ADD },
            onShow = { code ->
                identityShowId = code.id
                route = Route.IDENTITY_SHOW
            },
            onToggleDynamic = { id, dynamic -> repository.setIdentityDynamic(id, dynamic) },
            onDelete = { id -> repository.deleteIdentityCode(id) },
        )

        Route.IDENTITY_ADD -> IdentityEditScreen(
            existingCount = appData.identityCodes.size,
            apps = installedApps,
            onBack = { route = Route.IDENTITY_LIST },
            onSave = { label, value, dynamic, note, appPackage ->
                repository.addIdentityCode(label, value, dynamic, note, appPackage)
            },
        )

        Route.IDENTITY_SHOW -> {
            /* 按 id 现取。那张码可能刚被删掉，这时候必须退回去，而不是继续出示一张
               已经不存在的码——它屏上看起来仍然完全正常，而那正是最坏的失效。 */
            val code = appData.identityCodes.firstOrNull { it.id == identityShowId }
            if (code == null) {
                LaunchedEffect(Unit) { route = Route.IDENTITY_LIST }
            } else {
                IdentityShowScreen(
                    code = code,
                    appInstalled = code.appPackage?.let { repository.isAppInstalled(it) } == true,
                    onBack = { route = Route.IDENTITY_LIST },
                    onOpenApp = { openOfficialApp(code.appPackage.orEmpty()) },
                )
            }
        }

        Route.NOTIFY_APPS -> NotifyAppsScreen(
            apps = appData.notifyApps,
            now = now,
            onBack = { route = notifyReturn },
            onToggle = { pkg, disabled -> repository.setNotifyAppDisabled(pkg, disabled) },
        )

        Route.MANUAL_IMPORT -> ManualImportScreen(
            prefillRaw = manualPrefill,
            knownBodies = knownBodies,
            onBack = { route = manualReturn },
            onSave = { code, name, locker, raw, owner ->
                val stored = repository.manualAdd(code, name, locker, raw, owner, resolvesUnrecognizedOf = manualSourceBody)
                if (stored) {
                    manualSourceBody = null
                    manualPrefill = ""
                    // 从引导里手动导入成功 = 用户已经拿到价值，引导可以收尾了
                    if (manualReturn == Route.ONBOARDING) repository.prefs.onboardingDone = true
                    manualReturn = Route.HOME
                    route = Route.HOME
                }
                // 失败时留在原地：用户已经打好的字不能被清掉，那比一句报错重要。
                stored
            },
        )

        Route.POINT_DETAIL -> {
            val group = realGroups.firstOrNull { it.key == detailKey }
            val history = remember(detailKey, appData.aliases) { repository.mergeHistoryFor(detailKey) }
            PointDetailScreen(
                group = group,
                history = history,
                otherGroups = realGroups,
                aliases = appData.aliases,
                now = now,
                onBack = { route = Route.HOME },
                onOpenPoint = { g -> openPoint(g.key, g.name) },
                onSplit = { alias -> repository.undoMerge(alias.aliasKey) },
                onSetOwner = { parcel, owner -> repository.setParcelOwner(parcel.id, owner) },
                onMergeOtherIntoThis = { other ->
                    group?.let { openMergePreview(other, it, preferTarget = it) }
                },
            )
        }

        Route.MERGE_PREVIEW -> pendingMerge?.let { preview ->
            MergePreviewScreen(
                preview = preview,
                keepTarget = keepTarget,
                onKeepTarget = { keepTarget = it },
                onConfirm = { confirmMerge() },
                onCancel = { pendingMerge = null; route = mergeReturn },
            )
        }

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
                manualSourceBody = null
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
