package dev.showcode.core.parse

import dev.showcode.core.DEFAULT_CLOSING_MINUTE
import dev.showcode.core.LOCKER_FREE_HOURS_DEFAULT
import dev.showcode.core.Owner
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.pointKey

/** 一条待解析的原始消息。短信与 App 通知共用同一个入口。 */
data class IncomingMessage(
    /** 短信发件人号码，或通知的来源应用名。可能为空（网络短信/5G 消息就是空的）。 */
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val channel: Source,
    /** 谁的件。微信/QQ 转发进来的会被标成代取件，其余默认是自己的。 */
    val owner: Owner = Owner.SELF,
)

/** 解析成功产物，尚未落库（不含 id、pointKey 等派生字段）。 */
data class ParsedParcel(
    val code: String,
    val lockerNo: String?,
    val carrier: String,
    val pointName: String,
    val pointKind: PointKind,
    val closesAtMinuteOfDay: Int?,
    val freeUntilAt: Long?,
)

/**
 * 未识别的原因。必须是结构化的，不能是一句「解析失败」——
 * brief 的设计标准写死了：「提示必须列出漏掉了哪些来源，不说『权限不足』」。
 */
enum class UnrecognizedReason {
    /** 正文把取件码指向了小程序/App/公众号，短信里根本没有码。 */
    CODE_LIVES_IN_APP,

    /** 无发件人号码的网络短信（5G 消息类）。在位者 README 也承认这类识别不了。 */
    NO_SENDER_NUMBER,

    /** 有取件语义，但正文里找不到可识别的取件码。 */
    NO_CODE_FOUND,
}

sealed interface ParseOutcome {
    data class Parsed(val parcel: ParsedParcel) : ParseOutcome

    /** 是取件消息但没解析出码 → 落库为「未识别」，让用户知道漏了什么。 */
    data class Rejected(val reason: UnrecognizedReason) : ParseOutcome

    /**
     * 根本不是取件消息 → 静默丢弃，**不落库**。
     * 这一档的存在是「未识别」列表有用的前提：如果营销短信也算未识别，
     * 角标会常年是个无意义的数字，用户会直接忽略它。
     */
    data object Ignored : ParseOutcome
}

object PickupCodeParser {

    fun parse(message: IncomingMessage): ParseOutcome {
        val body = message.body
        if (!PICKUP_SIGNAL.containsMatchIn(body)) return ParseOutcome.Ignored

        val code = extractCode(body) ?: return ParseOutcome.Rejected(reasonOf(body, message.sender))

        val pointName = extractPointName(body)
        val kind = kindOf(pointName, body)
        val closingMinute = if (kind == PointKind.LOCKER) null else closingMinuteOf(body, kind)
        val freeUntil = if (kind == PointKind.LOCKER) {
            message.receivedAt + (freeHoursOf(body) ?: LOCKER_FREE_HOURS_DEFAULT) * 60L * 60 * 1000
        } else {
            null
        }

        return ParseOutcome.Parsed(
            ParsedParcel(
                code = code,
                lockerNo = extractLockerNo(body),
                carrier = carrierOf(body),
                pointName = pointName,
                pointKind = kind,
                closesAtMinuteOfDay = closingMinute,
                freeUntilAt = freeUntil,
            )
        )
    }

    /** 由 ParsedParcel 生成入库用的取件点标识（保守归一化，见 core.pointKey）。 */
    fun pointKeyOf(parsed: ParsedParcel): String = pointKey(parsed.pointName)

    // ── 是否为取件消息 ────────────────────────────────
    // 门槛要够高：物流轨迹通知（「已到达XX中转中心」）、营销短信都不能进来，
    // 否则「未识别」列表会被垃圾填满，角标随之失效。
    private val PICKUP_SIGNAL = Regex(
        "取件|提货|取货|已到站|已入柜|已放入|已投递|投递到|快递柜|驿站|自提点|丰巢|包裹已到|快递已到"
    )

    // ── 取件码 ────────────────────────────────────────
    private val CODE_PATTERNS = listOf(
        Regex("(?:取件码|提货码|取货码|取件号|取件密码|领取码|取件验证码)\\s*[:：是为]?\\s*([0-9A-Za-z][0-9A-Za-z\\-]{1,14})"),
        Regex("凭\\s*([0-9A-Za-z][0-9A-Za-z\\-]{1,14})\\s*(?:取件|提货|领取|取货|取)"),
    )
    private val PHONE_NUMBER = Regex("1\\d{10}")

    private fun extractCode(body: String): String? = CODE_PATTERNS
        .firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }
        ?.trim()
        ?.takeIf { it.length in 2..12 && !PHONE_NUMBER.matches(it) }

    // ── 格口号 / 货架号 ───────────────────────────────
    // 只取值，不取「格口」「货架」这两个字：那两个字是 UI 按取件点类型加的前缀。
    private val LOCKER_PATTERNS = listOf(
        Regex("(?:格口|货格|柜号|格子|箱号)\\s*号?\\s*[:：]?\\s*([^\\s，,。；;！!？?]{1,10})"),
        Regex("(?:货架|架子)\\s*号?\\s*[:：]?\\s*([^\\s，,。；;！!？?]{1,12})"),
        Regex("([0-9]{1,2}\\s*排\\s*[0-9]{1,3}\\s*号?)"),
        // 「到9号柜取件」「请到五号柜取件」：没有锚定词，柜号就在「N号柜」这三个字里。
        // 汉字那一种是 issue #56 专门报过的。放在最后，别抢走带锚定词的写法。
        Regex("([0-9一二两三四五六七八九十百]{1,4}\\s*号\\s*柜)"),
    )

    /** 柜号形状：必须含数字、拉丁字母或中文数字，否则它是个短语不是一个号。 */
    private val LOCKER_NO_SHAPE = Regex("[0-9A-Za-z一二两三四五六七八九十百零]")
    private val WHITESPACE = Regex("\\s")

    /**
     * 取第一个**形状合法**的匹配，而不是取第一个匹配。
     * 「到人工货架取包裹」会让货架那条抓到「取包裹」，如果先匹配先返回，
     * 同一条里真正有效的柜号就再也没机会被尝试了。
     */
    private fun extractLockerNo(body: String): String? = LOCKER_PATTERNS
        .asSequence()
        .mapNotNull { pattern -> pattern.find(body)?.groupValues?.get(1) }
        .map { WHITESPACE.replace(it, "") }
        .firstOrNull { LOCKER_NO_SHAPE.containsMatchIn(it) }

    // ── 取件点名称 ────────────────────────────────────
    // 续接位也要排除空白：App 通知的 bigText 是按行分段的
    // （「您有一件包裹已到达⏎向阳小区菜鸟驿站(08号店)⏎取件码 7-1-3393⏎请及时取件」），
    // 只挡标点会让取件码和提示语整段住进卡片标题。
    // 代价是名字里不能带空格——这些文案里空格和换行一样是分隔符，宁可名字短一截。
    private val POINT_NAME = Regex(
        "(?:已到达|已到站|已到|已送至|已放入|已投放|投放到|投递到|投递至|存放于|存放在|放入|到达)" +
            "\\s*([^，,。；;！!？?\\s][^，,。；;！!？?\\s]{1,39})"
    )
    private val BRAND_TAG = Regex("【(.{1,20}?)】")
    private val NAME_PREFIX_NOISE = Regex("^(您的|你的|包裹|快递|快件|一个|您的包裹)+")
    private val BRAND_FALLBACK = Regex("丰巢|菜鸟驿站|菜鸟|京东自提|京东|顺丰|中通|圆通|韵达|申通|邮政|极兔")

    private fun extractPointName(body: String): String {
        val fromVerb = POINT_NAME.find(body)?.groupValues?.get(1)
            ?.replace(NAME_PREFIX_NOISE, "")
            ?.trim()
            ?.takeIf { it.length >= 2 }
        if (fromVerb != null) return fromVerb

        // 没有点位名时退到品牌名。注意这会让同品牌的多个未知点位合并成一张卡片——
        // 保守归一化只承诺「不合并名称不同的点」，这里两次的名称是同一个（都未知）。
        val tag = BRAND_TAG.find(body)?.groupValues?.get(1)
        val brand = BRAND_FALLBACK.find(tag ?: body)?.value
        return when {
            brand == "丰巢" -> "丰巢快递柜"
            brand != null && brand.startsWith("菜鸟") -> "菜鸟驿站"
            brand != null -> brand + "自提点"
            else -> "取件点"
        }
    }

    private val KIND_STATION = Regex("驿站")
    private val KIND_LOCKER = Regex("丰巢|智能柜|快递柜|蜂巢|菜鸟柜|速递易|保管箱|柜")
    private val KIND_SELFPICK = Regex("自提|便民|便利店|超市|门店")

    /** 先看抽出来的点位名，点位名为空才看正文——正文里顺带提到「柜」的情况很常见。 */
    private fun kindOf(pointName: String, body: String): PointKind {
        val hint = pointName.ifBlank { body }
        return when {
            KIND_STATION.containsMatchIn(hint) -> PointKind.STATION
            KIND_LOCKER.containsMatchIn(hint) -> PointKind.LOCKER
            KIND_SELFPICK.containsMatchIn(hint) -> PointKind.SELFPICK
            else -> PointKind.STATION
        }
    }

    // ── 快递公司 ──────────────────────────────────────
    private val CARRIERS = listOf(
        "顺丰速运" to Regex("顺丰"),
        "中通快递" to Regex("中通"),
        "圆通速递" to Regex("圆通"),
        "韵达快递" to Regex("韵达"),
        "申通快递" to Regex("申通"),
        "京东物流" to Regex("京东物流|京东快递|京东"),
        "邮政EMS" to Regex("邮政|EMS"),
        "极兔速递" to Regex("极兔"),
        "百世快递" to Regex("百世"),
        "德邦快递" to Regex("德邦"),
    )

    private fun carrierOf(body: String): String =
        CARRIERS.firstNotNullOfOrNull { (name, re) -> name.takeIf { re.containsMatchIn(body) } } ?: "快递"

    // ── 时效依据 ──────────────────────────────────────
    // 两种词序都要认：「18小时内免费保管」和「免费保管24小时」。
    // 只认前一种会静默退到默认 18，而默认值常常恰好是对的——于是这个洞永远暴露不了自己。
    private val FREE_HOURS = listOf(
        Regex("(\\d{1,3})\\s*(?:小时|個小時)\\s*内?\\s*免费"),
        Regex("免费(?:保管|存放)?\\s*(\\d{1,3})\\s*(?:小时|個小時)"),
    )
    private val BUSINESS_RANGE =
        Regex("营业时间[^0-9]{0,6}(\\d{1,2})\\s*[:：.]\\s*(\\d{2})\\s*[-~至到]\\s*(\\d{1,2})\\s*[:：.]\\s*(\\d{2})")

    // 第一个捕获组是 12 小时制的中文修饰：不认它，「下午6点前取件」会算成 06:00，
    // 于是那件从早上起就全天标着「过期风险」。
    private val CLOSE_BEFORE = Regex(
        "(凌晨|早上|上午|中午|下午|晚上|晚)?\\s*(\\d{1,2})\\s*[:：点]\\s*(\\d{0,2})\\s*前(?:取件|领取|来取|取走|前来|取)"
    )

    private fun freeHoursOf(body: String): Int? = FREE_HOURS
        .firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }
        ?.toIntOrNull()?.takeIf { it in 1..168 }

    private fun shiftHour(meridiem: String, hour: Int): Int = when (meridiem) {
        "下午", "晚上", "晚" -> if (hour in 1..11) hour + 12 else hour
        "中午" -> if (hour in 1..5) hour + 12 else hour
        else -> hour
    }

    /**
     * 短信里写了营业时间/截止时间就用短信的；没写就退到内置默认。
     * 默认值是占位（见 DEFAULT_CLOSING_MINUTE 的说明），营业时间库是 V2 的数据资产。
     */
    private fun closingMinuteOf(body: String, kind: PointKind): Int? {
        BUSINESS_RANGE.find(body)?.let { m ->
            val h = m.groupValues[3].toIntOrNull() ?: return@let
            val min = m.groupValues[4].toIntOrNull() ?: return@let
            if (h in 0..23 && min in 0..59) return h * 60 + min
        }
        CLOSE_BEFORE.find(body)?.let { m ->
            val h = m.groupValues[2].toIntOrNull() ?: return@let
            val min = m.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: return@let
            val shifted = shiftHour(m.groupValues[1], h)
            if (shifted in 0..23 && min in 0..59) return shifted * 60 + min
        }
        return DEFAULT_CLOSING_MINUTE[kind]
    }

    private val CODE_IN_APP = Regex("小程序|公众号|客户端|APP|App|app|微信|支付宝")

    private fun reasonOf(body: String, sender: String): UnrecognizedReason = when {
        sender.isBlank() -> UnrecognizedReason.NO_SENDER_NUMBER
        CODE_IN_APP.containsMatchIn(body) -> UnrecognizedReason.CODE_LIVES_IN_APP
        else -> UnrecognizedReason.NO_CODE_FOUND
    }
}
