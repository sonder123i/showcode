package dev.showcode.core

/**
 * 打开一家官方 App 的兜底链。
 *
 * 存在的理由：竞品 issue #39 的全部内容是「点了没反应」，而维护者给的解法是去系统里开
 * 「链式启动管理」。**一条路走不通是常态，不是一种意外**——所以这一格必须自己走完几级，
 * 并且最后一级失败时说话，而不是静默。
 *
 * ⚠️ 那两条深链的出处是 `shareven/parcel` 的 `util/ThirdPartyEntry.kt`（我逐字读过，
 * 不是猜的）。它们**随对方版本变**，而我们看不到对方的版本：这正是我把它们做成
 * 「第一级尝试」而不是「唯一一条路」的原因。竞品自己也在后面留了退路。
 *
 * 层级从「最有用」排到「最保底」：
 * 1. 直达那家 App 里的取件页（省三步，但可能坏）；
 * 2. 该 App 的启动入口（只到首页，却几乎不会坏）；
 * 3. 用系统浏览器打开那个取件页（**那个页面本身是 H5**，没装 App 的人也走得通）。
 */
object DeepLinkLadder {

    const val TAOBAO = "com.taobao.taobao"
    const val PINDUODUO = "com.xunmeng.pinduoduo"
    /** 菜鸟（驿站会员码/取件码那一类）。包名核实自应用宝的官方详情页 `a.app.qq.com/o/simple.jsp?pkgname=…`。 */
    const val CAINIAO = "com.cainiao.wireless"
    /** 丰巢（柜机寄件码）。包名核实自应用宝的详情页 `sj.qq.com/appdetail/…`，单一来源。 */
    const val FENGCHAO = "com.fcbox.hiveconsumer"

    /** 淘宝驿站取件页。竞品那条 tbopen 的 h5Url 就是它。 */
    private const val TAOBAO_LASTMILE =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
    private const val TAOBAO_OPEN_PREFIX = "tbopen://m.taobao.com/tbopen/index.html?h5Url="
    private const val PDD_PACKAGE_PAGE = "pinduoduo://com.xunmeng.pinduoduo/mdkd/package"

    /**
     * 预置名单，顺序就是屏上的顺序。
     *
     * ⚠️ 后两家**没有直达链**：它们走第二级（启动入口），所以点了落在那家 App 的首页，
     * 不是身份码那一页。这不是漏了深链，是我们确实不知道那条 scheme——竞品那位维护者说的
     * 也就是「只是跳转」这一层。有直达链的两家排在前面，让省三步的那些先被看到。
     */
    val knownPackages: List<String> = listOf(TAOBAO, PINDUODUO, CAINIAO, FENGCHAO)

    /**
     * 预置那一排「一键去取」该列哪几家：[knownPackages] ∩ 这台机器真装着的，顺序按已知表。
     *
     * 这条限制是判据不是细节：**一颗点了只会失败的按钮比没有更坏**，而站在这一格的人
     * 常常正抱着两个包裹。装没装是 data 层查出来的事实，怎么筛是纯的，所以筛法在这儿。
     */
    fun entriesFor(installedPackages: Set<String>): List<String> {
        val have = installedPackages.mapTo(mutableSetOf()) { it.trim().lowercase() }
        return knownPackages.filter { it in have }
    }

    /** 第一级：直达页的 scheme URI。不认识的包返回空表，直接跳到第二级。 */
    fun deepLinksFor(packageName: String?): List<String> = when (packageName?.trim()?.lowercase()) {
        TAOBAO -> listOf(TAOBAO_OPEN_PREFIX + percentEncode(TAOBAO_LASTMILE))
        PINDUODUO -> listOf(PDD_PACKAGE_PAGE)
        else -> emptyList()
    }

    /** 第三级：系统浏览器兜底的那个网页。只有直达页本身就是 http(s) 的才有。 */
    fun webFallbackFor(packageName: String?): String? =
        when (packageName?.trim()?.lowercase()) {
            TAOBAO -> TAOBAO_LASTMILE
            else -> null
        }

    /**
     * 查询参数percent编码。
     *
     * core 里不能引 `android.net.Uri`（那会把这一格从可单测变成要设备），而 `h5Url=` 后面
     * 那个地址里的 `:` 和 `/` 不编码的话，淘宝的参数解析器会把它截断成 `https:`。
     */
    fun percentEncode(raw: String): String = buildString {
        for (ch in raw) {
            val code = ch.code
            val unreserved = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code ||
                code in '0'.code..'9'.code || ch == '-' || ch == '_' || ch == '.' || ch == '~'
            if (unreserved) append(ch) else append('%').append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/** 某一级尝试的结果。「没有处理器」和「有处理器但被拦住」必须是两个值——见 [launchOutcome]。 */
enum class Attempt { OPENED, NO_HANDLER, REFUSED }

/**
 * 由每一级的结果推结局。顺序是刻意的：
 *
 * - 只要有一级真起来了就是 [LaunchOutcome.Opened]，不去管它是第几级——用户不关心。
 * - **全都是 `NO_HANDLER` 才是「没装」**。掺进一个 `REFUSED` 就说明系统里有东西在拦，
 *   那是 ROM 的开关问题，叫用户去重装一个已经装好的 App 是把他支走。
 * - 一上来就没包名（这张码没记过是哪家）是另一件事，不许和「没装」共用一句话。
 */
fun launchOutcome(hasPackage: Boolean, attempts: List<Attempt>): LaunchOutcome = when {
    !hasPackage -> LaunchOutcome.NoTarget
    attempts.contains(Attempt.OPENED) -> LaunchOutcome.Opened
    attempts.isNotEmpty() && attempts.all { it == Attempt.NO_HANDLER } -> LaunchOutcome.NotInstalled
    else -> LaunchOutcome.Blocked
}
