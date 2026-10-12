package dev.showcode.core

/**
 * 三态时效。frame.json → design_decisions.urgency_thresholds 已锁定为三态，不是四态：
 * 紧迫度梯度不靠第四个色阶，靠倒计时数字本身的连续变化承载。
 * order 就是默认排序权重（Story 2 AC3：最急的在最上面，不是最新到达的在最上面）。
 */
enum class Urgency(val order: Int) {
    AT_RISK(0),
    DUE_SOON(1),
    SAFE(2),
}

enum class PointKind { STATION, LOCKER, SELFPICK }

/** 代取件是一等类别（edge s4-onsite.boundary-daqu），不是附注。 */
enum class Owner { SELF, FAMILY }

enum class Source { SMS, NOTIFICATION, MANUAL, DEMO }

/**
 * 读库时的枚举兜底：名字对不上就当不认识，用调用方给的默认值，**绝不抛**。
 *
 * `valueOf` 直接用在行解码上意味着：只要以后给这些枚举改过一次名（或者用户装了更旧的 APK），
 * `reload()` 每次读表都会炸，整个 App 打不开。这里宁可丢那一个字段的精度，
 * 也不能让一列文本决定用户能不能看见自己的取件码。
 */
inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, fallback: E): E =
    raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

/**
 * 一件待取包裹。
 *
 * 时效不入库：urgency 是 (deadline, now) 的派生值，存下来就会过期。
 * 入库的只有两个截止时刻来源——柜子的免费保管截止 [freeUntilAt]，
 * 以及驿站/自提点的关门时刻 [closesAtMinuteOfDay]。
 */
data class Parcel(
    val id: Long,
    val code: String,
    /** 格口号 / 货架号。可能缺失；缺失时现场模式整块隐藏、取件码变大（不是留白，是重排）。 */
    val lockerNo: String?,
    val carrier: String,
    /** 归一化后的取件点标识，同时用作首页一级 IA 的分组键。 */
    val pointKey: String,
    /** 取件点显示名。首页可截断，现场模式必须完整显示（用户靠它确认「我来对地方了」）。 */
    val pointName: String,
    val pointKind: PointKind,
    val owner: Owner,
    val source: Source,
    val receivedAt: Long,
    /** 快递柜免费保管截止（epoch millis）。非柜类为 null。 */
    val freeUntilAt: Long?,
    /** 驿站/自提点关门时刻，一天内的分钟数。未知为 null——不编造营业时间。 */
    val closesAtMinuteOfDay: Int?,
    val picked: Boolean,
    val pickedAt: Long?,
    /** 原始短信/通知正文。合并历史与「未识别」复核都要用原文，不能只留解析结果。 */
    val raw: String,
)

/** 首页的一级公民：取件点，不是包裹。 */
data class PickupPointGroup(
    val key: String,
    val name: String,
    val kind: PointKind,
    val closesAtMinuteOfDay: Int?,
    /** 已按紧急度排序的待取件。 */
    val items: List<Parcel>,
    /** 该点的时效代表 = 所有待取件里最急的一档。 */
    val worst: Urgency,
)
