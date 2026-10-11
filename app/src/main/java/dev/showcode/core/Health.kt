package dev.showcode.core

/**
 * 权限健康。story-5 的前提：权限失效是**静默**的——系统不会通知用户，
 * 他只会觉得「最近好像没新快递」，然后把锅甩给「最近没买东西」。
 * 所以这个模型必须常驻自检，不能只在 onboarding 检查一次。
 */
enum class HealthIssue {
    /** 短信权限被拒或被系统收回：主通道没了。 */
    SMS_DENIED,

    /** 通知监听没开：微信/菜鸟/京东/丰巢 的推送来源全漏。 */
    NOTIFICATION_DENIED,

    /** 权限都正常却长时间没同步过：最可能的解释是后台进程被国产 ROM 杀了。 */
    BACKGROUND_SUSPECTED,

    /** 小米「通知类短信」开关。无法程序化查询，只能按厂商常驻提醒，且只进体检表不进横条。 */
    MIUI_SMS_CLASS,
}

data class HealthReport(
    /** 可程序化判定的失效项，按严重度排序。首页横条只报这里的最严重一项。 */
    val issues: List<HealthIssue>,
    /** 只能人工确认的提醒项（分机型开关）。只出现在体检表里，不参与横条。 */
    val manualChecks: List<HealthIssue>,
) {
    val worst: HealthIssue? get() = issues.firstOrNull()

    /** 除最严重一项外还有几项——横条上的「还有 N 项」。 */
    val restCount: Int get() = (issues.size - 1).coerceAtLeast(0)

    val allGood: Boolean get() = issues.isEmpty() && manualChecks.isEmpty()
}

/** 权限都正常却这么久没同步过，就怀疑后台被限制。6 小时是保守值：宁可晚报，不可误报。 */
const val BACKGROUND_SUSPECT_MS = 6 * HOUR_MS

fun healthReport(
    smsGranted: Boolean,
    notificationEnabled: Boolean,
    lastSyncAt: Long?,
    now: Long,
    isXiaomi: Boolean,
): HealthReport {
    val issues = buildList {
        if (!smsGranted) add(HealthIssue.SMS_DENIED)
        if (!notificationEnabled) add(HealthIssue.NOTIFICATION_DENIED)
        if (smsGranted && notificationEnabled && lastSyncAt != null && now - lastSyncAt > BACKGROUND_SUSPECT_MS) {
            add(HealthIssue.BACKGROUND_SUSPECTED)
        }
    }
    val manualChecks = buildList {
        if (isXiaomi && smsGranted) add(HealthIssue.MIUI_SMS_CLASS)
    }
    return HealthReport(issues = issues, manualChecks = manualChecks)
}
