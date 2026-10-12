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

    /**
     * 码认出来了，但**这个件属于哪个取件点没把握**。
     *
     * 走这条而不是生成一张卡，是因为兜底出来的名字（「菜鸟驿站」「丰巢快递柜」「取件点」）
     * 会把不同门店合成同一张——用户站在 A 点看着 B 点的码，而且毫不怀疑。
     * 一张可疑的卡比一条待确认的未识别更危险。
     */
    POINT_NAME_UNCONFIRMED,
}

sealed interface ParseOutcome {
    /**
     * 一条消息可能是**多个件**：同一次网购拆包、同一驿站一次到几件，
     * 真实写法就是「取件码 5-1-2277、5-2-3391」。
     *
     * 只取第一个的话，第二个既不入库也不落未识别——那是永久消失，不是延后处理。
     * 顺序即正文里的出现顺序。
     */
    data class Parsed(val parcels: List<ParsedParcel>) : ParseOutcome

    /**
     * @param codes 已经认出来的码。带着它，用户只需补那一个不确定的字段
     *              （通常是取件点名称），不必重新抄一遍码。
     */
    data class Rejected(val reason: UnrecognizedReason, val codes: List<String> = emptyList()) : ParseOutcome

    /**
     * 与取件无关 → 静默丢弃，**不落库**。两种情况都归这里：根本不是取件消息
     * （营销、物流轨迹、验证码），以及抽得出数字但既没有强锚定也没有取件语境
     * （「请使用 889900 完成支付」）。
     *
     * 这一档的存在是「未识别」列表有用的前提：如果营销短信也算未识别，
     * 角标会常年是个无意义的数字，用户会直接忽略它。
     */
    data object Ignored : ParseOutcome
}

object PickupCodeParser {

    fun parse(message: IncomingMessage): ParseOutcome {
        val body = message.body
        val hits = extractCodes(body)
        val primary = hits.firstOrNull()
        return when {
            primary != null && MessageGate.admitsCode(body, strong = primary.strong) -> {
                val name = extractPointName(body)
                if (name.confirmed) {
                    parsed(message, body, hits, name.value)
                } else {
                    // 码是确定的、点是不确定的：把码一起带进未识别，用户只补那一个字段。
                    ParseOutcome.Rejected(
                        UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                        hits.map { it.value },
                    )
                }
            }
            MessageGate.looksLikePickup(body) -> ParseOutcome.Rejected(reasonOf(body, message.sender))
            else -> ParseOutcome.Ignored
        }
    }

    private fun parsed(
        message: IncomingMessage,
        body: String,
        hits: List<CodeHit>,
        pointName: String,
    ): ParseOutcome.Parsed {
        val kind = kindOf(pointName, body)
        val closingMinute = if (kind == PointKind.LOCKER) null else closingMinuteOf(body, kind)
        val freeUntil = if (kind == PointKind.LOCKER) {
            message.receivedAt + (freeHoursOf(body) ?: LOCKER_FREE_HOURS_DEFAULT) * 60L * 60 * 1000
        } else {
            null
        }
        // 多件时柜号不填：一条短信里只有一个「格口A12」，却对应两个件，
        // 猜给谁都是赌。现场少显示一个柜号，远好过把人指向另一个格口。
        val lockerNo = if (hits.size > 1) null else extractLockerNo(body)

        return ParseOutcome.Parsed(
            hits.map {
                ParsedParcel(
                    code = it.value,
                    lockerNo = lockerNo,
                    carrier = carrierOf(body),
                    pointName = pointName,
                    pointKind = kind,
                    closesAtMinuteOfDay = closingMinute,
                    freeUntilAt = freeUntil,
                )
            },
        )
    }

    /** 由 ParsedParcel 生成入库用的取件点标识（保守归一化，见 core.pointKey）。 */
    fun pointKeyOf(parsed: ParsedParcel): String = pointKey(parsed.pointName)

    // ── 取件码 ────────────────────────────────────────
    /**
     * 命中结果。[strong] = 锚定词本身就带取件语义，可以越过负向锚直接进门；
     * 弱锚定必须由语境担保，见 [MessageGate]。
     */
    data class CodeHit(val value: String, val strong: Boolean)

    // 「N号柜」前缀是可选的非捕获组：柜号与码粘连（issue #29 的「取件码 4号柜719077」）
    // 不该让整条抽不到码，而柜号本身另有 LOCKER_PATTERNS 负责取出来。
    private val CODE_PATTERNS_STRONG = listOf(
        Regex(
            "(?:取件码|提货码|取货码|取件号|取件密码|领取码|取件验证码|开柜码|柜机码)" +
                "\\s*[:：是为]?\\s*(?:[0-9一二两三四五六七八九十]{1,3}\\s*号\\s*柜\\s*)?" +
                "([0-9A-Za-z][0-9A-Za-z\\-]{1,14})"
        ),
        Regex("凭\\s*([0-9A-Za-z][0-9A-Za-z\\-]{1,14})\\s*(?:取件|提货|领取|取货|取)"),
    )

    /** 句式锚定：光凭这几个字不能断定是取件，所以进门时要语境担保。 */
    private val CODE_PATTERNS_WEAK = listOf(
        // 「请用T8-99到人工货架取包裹」「请凭数字密码 8-9-0765 到柜领取」
        Regex(
            "(?:请用|请凭|使用|凭)(?:数字)?(?:密码|码)?\\s*[:：是为]?" +
                "\\s*([0-9A-Za-z][0-9A-Za-z\\-]{1,14})"
        ),
        // 「到店输入02996或扫码亮灯取件」——没有「取件码」三个字，码就在「输入」后面
        Regex("输入\\s*[:：是为]?\\s*(\\d{4,8})"),
    )

    private val PHONE_NUMBER = Regex("1\\d{10}")

    /** 一条消息最多切成几个件。粘贴进来的一整页文本不该一次生成几十件；超出就是有意截断。 */
    private const val MAX_PARCELS_PER_MESSAGE = 12

    /** 紧跟量词的候选是数量不是码：「，24小时内免费」里的 24 不能变成一个假件。 */
    private val FOLLOWER_UNIT = Regex("^(小时|个小时|個小時|天|月|日|年|元|点|分|秒|折|单|箱)")

    private fun extractCodes(body: String): List<CodeHit> =
        hits(body, CODE_PATTERNS_STRONG, strong = true).ifEmpty { hits(body, CODE_PATTERNS_WEAK, strong = false) }

    /** 保持「强锚定整条优先」：第一个能抓到码的锚定词组赢，后面的组不再参与。 */
    private fun hits(body: String, patterns: List<Regex>, strong: Boolean): List<CodeHit> {
        for (pattern in patterns) {
            val codes = LinkedHashSet<String>()
            pattern.findAll(body).forEach { match ->
                val primary = match.groupValues[1].asCode() ?: return@forEach
                codes += primary
                codes += followers(body, match.range.last, primary)
            }
            if (codes.isNotEmpty()) {
                return codes.take(MAX_PARCELS_PER_MESSAGE).map { CodeHit(it, strong) }
            }
        }
        return emptyList()
    }

    /**
     * 主码之后被顿号/逗号/斜杠/「和」「及」或空格串起来的**同形**码。
     * 同形是关键的精度闸门：「取件码 1234、A12」里 A12 是格口，不是第二个件。
     */
    private fun followers(body: String, primaryEnd: Int, primary: String): List<String> {
        val shape = if ('-' in primary) "[0-9A-Za-z]*-[0-9A-Za-z\\-]{1,13}" else "\\d{2,12}"
        val separator = Regex("[、,，/和及\\s]{1,3}")
        val candidate = Regex("^($shape)")
        val out = mutableListOf<String>()
        var rest = body.substring(primaryEnd + 1)
        while (true) {
            val gap = separator.find(rest)?.value ?: break
            rest = rest.substring(gap.length)
            val match = candidate.find(rest) ?: break
            if (FOLLOWER_UNIT.containsMatchIn(rest.substring(match.value.length))) break
            val code = match.groupValues[1].asCode() ?: break
            out += code
            rest = rest.substring(match.value.length)
        }
        return out
    }

    private fun String?.asCode(): String? =
        this?.trim()?.takeIf { it.length in 2..12 && !PHONE_NUMBER.matches(it) }

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

    /** [confirmed] = 这个名字携带了具体门店信息，还是只有品牌 + 类别词。 */
    private data class PointNameHit(val value: String, val confirmed: Boolean)

    /** 方括号与引号里装的是占位符或整句的尾巴，不是一个点位名。 */
    private val PLACEHOLDER_MARKS = Regex("[【】“”\"「」]")

    /**
     * 通用名：只有品牌加类别词，不含任何具体门店信息。
     *
     * 判「这个名字能不能用」必须看名字本身，而不是看它是从哪抽出来的——
     * 「丰巢柜」即使是从「已放入丰巢柜」里抽的，也一样会把小区门口两台柜机合成一张卡。
     */
    private val GENERIC_NAME = Regex(
        "^(丰巢|菜鸟|京东|顺丰|中通|圆通|韵达|申通|邮政|极兔|兔喜)?" +
            "(快递柜|智能柜|自提柜|自提点|代收点|驿站|取件点|柜机|柜)$"
    )

    /**
     * 拼多多 / 兔喜的分享文本是分段式的：「取件码 X⏎韵达快递超市代收点 | 富里红波精品超市」。
     * 竖线**后面**才是门店，前面那半截是品牌加类别。这类文本没有任何短信动词，
     * 以前只能整条送确认——而它其实带着完整的点名信息。
     */
    private val POINT_NAME_PIPE = Regex("[|｜]\\s*([^|｜\\n，,。；;！!？?]{2,40})")

    private fun extractPointName(body: String): PointNameHit {
        val fromVerb = POINT_NAME.find(body)?.groupValues?.get(1)
            ?.replace(NAME_PREFIX_NOISE, "")
            ?.trim()
            ?.takeIf { it.length >= 2 }

        // 动词抽到的名字如果是个占位符或通用名，就不算确认——但先试竖线分段，
        // 因为粘贴式文本里真正的门店名只出现在竖线后面。
        val fromPipe = POINT_NAME_PIPE.find(body)?.groupValues?.get(1)?.trim()

        for (candidate in listOf(fromVerb, fromPipe)) {
            if (candidate != null && candidate.length >= 2 &&
                !PLACEHOLDER_MARKS.containsMatchIn(candidate) &&
                !GENERIC_NAME.matches(candidate)
            ) {
                return PointNameHit(candidate, confirmed = true)
            }
        }

        // 走到这里就是消息里根本没有携带「是哪个点」的信息。以前会退成「菜鸟驿站」「丰巢快递柜」
        // 直接成卡，那会把不同小区合成一张——用户站在 A 点看着 B 点的码且毫不怀疑。
        // 现在这个名字只用来把消息解释给人看，件本身送未识别等确认（POINT_NAME_UNCONFIRMED）。
        val tag = BRAND_TAG.find(body)?.groupValues?.get(1)
        val brand = BRAND_FALLBACK.find(tag ?: body)?.value
        val guess = when {
            brand == "丰巢" -> "丰巢快递柜"
            brand != null && brand.startsWith("菜鸟") -> "菜鸟驿站"
            brand != null -> brand + "自提点"
            else -> "取件点"
        }
        return PointNameHit(guess, confirmed = false)
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
