package dev.showcode.core

/**
 * 取件点合并（story-6）。
 *
 * 核心手法：合并**不改写 parcel 行**，只登记一条「A 这个点是 B 的别名」，
 * 分组时在读取侧解析。于是「拆开」= 删掉那条登记，AC3（拆开之后合并前的所有
 * 原始名称都还在，不会丢）就是结构上必然成立的，而不是靠回滚写回正确的值。
 * 副作用正好是我们要的：合并后新到的、写着旧名字的短信会自动落到同一个点。
 */

/** 一条已生效的合并登记：[aliasKey] 这个点被并到 [targetKey]。 */
data class PointAlias(
    val aliasKey: String,
    /** 合并发生时源点的显示名，用于合并历史里回显「把谁并进了谁」。 */
    val aliasName: String,
    val targetKey: String,
    val targetName: String,
    val createdAt: Long,
)

/** 解析一个点的归属：顺着别名走到底。带环保护，最多多跳 [MAX_ALIAS_HOPS] 次。 */
fun resolvePointKey(key: String, aliases: Map<String, PointAlias>): String {
    var current = key
    val seen = HashSet<String>()
    repeat(MAX_ALIAS_HOPS) {
        if (!seen.add(current)) return current
        current = aliases[current]?.targetKey ?: return current
    }
    return current
}

/** 两个键是否已经指向同一个点。合并与轻提示都要靠它挡住无意义操作。 */
fun isSamePoint(a: String, b: String, aliases: Map<String, PointAlias>): Boolean =
    resolvePointKey(a, aliases) == resolvePointKey(b, aliases)

/**
 * 合并后的点该显示哪个名字：用登记里目标点当时的名字，而不是最近一条短信里的写法。
 * 否则用户选了「合并到 菜鸟驿站(XX店)」，第二天来一条写着「XX便利店」的短信，
 * 卡片名字就会被他刚否掉的那个写法带跑。没被并过的点返回 null，由调用方回退。
 */
fun canonicalNameOf(key: String, aliases: Collection<PointAlias>): String? =
    aliases.filter { it.targetKey == key }.maxByOrNull { it.createdAt }?.targetName

/** 参与合并的一侧：件数 + 原始名称样本。 */
data class MergeSide(
    val key: String,
    val name: String,
    val kind: PointKind,
    val count: Int,
    /** 该点最近几次的不同写法（去重、按时间新到旧）。 */
    val nameSamples: List<String>,
)

/**
 * 合并预览（AC1）。用户要用证据判断而不是凭记忆，所以这里给的是
 * 两边各有多少件、合并后多少件、以及两边各自最近的原始名称。
 */
data class MergePreview(
    val source: MergeSide,
    val target: MergeSide,
    val total: Int,
)

fun mergePreview(source: PickupPointGroup, target: PickupPointGroup, sampleLimit: Int = 3): MergePreview {
    val a = MergeSide(source.key, source.name, source.kind, source.items.size, nameSamplesOf(source.items, sampleLimit))
    val b = MergeSide(target.key, target.name, target.kind, target.items.size, nameSamplesOf(target.items, sampleLimit))
    return MergePreview(a, b, a.count + b.count)
}

/**
 * 一个点历史上出现过的写法。保守归一化会把「XX便利店」和「xx 便利店」当成
 * 两个点，但同一个 pointKey 内部本来就可能有好几种拼法——预览要把这些摊出来，
 * 用户看到的才是证据而不是又一个他不再信的字符串。
 */
fun nameSamplesOf(parcels: List<Parcel>, limit: Int = 3): List<String> =
    parcels.sortedByDescending { it.receivedAt }.map { it.pointName }.distinct().take(limit)

/** 一对「可能是同一个点」的候选，按 [PickupPointGroup.key] 定序，便于做去重键。 */
data class SuspectPair(val first: PickupPointGroup, val second: PickupPointGroup) {
    /** 用户点「不是」之后要长期记住这条判断（V2 的社区别名数据不得覆盖本地表态）。 */
    val dismissKey: String get() = pairKey(first.key, second.key)
}

fun pairKey(a: String, b: String): String =
    if (a <= b) "$a\u0000$b" else "$b\u0000$a"

/**
 * 疑似重复的轻提示候选（AC4）。红线是**绝不自动合并**，所以这里宁可漏报：
 * 只有同一种点类型、且名称共享足够长的公共片段（或一方完整包含另一方）才提出来。
 *
 * 诚实的边界：像「XX便利店」与「菜鸟驿站(XX小区店)」这种**没有字面重叠**的同址
 * 情况，本规则抓不到——那需要地址数据（V2 的别名映射库），不是字符串能推出来的。
 */
fun suspectDuplicates(
    groups: List<PickupPointGroup>,
    dismissed: Set<String> = emptySet(),
    minCommon: Int = DEFAULT_MIN_COMMON,
): List<SuspectPair> {
    val ordered = groups.sortedWith(pointOrder())
    return buildList {
        for (i in ordered.indices) for (j in i + 1 until ordered.size) {
            val a = ordered[i]
            val b = ordered[j]
            if (a.kind != b.kind) continue
            if (pairKey(a.key, b.key) in dismissed) continue
            if (maxCommonFragment(a, b) < minCommon) continue
            add(SuspectPair(a, b))
        }
    }
}

/** 两侧所有写法里最相似的那一对共有多少个连续字符。比对的是观察到的写法，不是不透明的键。 */
fun maxCommonFragment(a: PickupPointGroup, b: PickupPointGroup): Int {
    val left = (listOf(a.name) + a.items.map { it.pointName }).distinct()
    val right = (listOf(b.name) + b.items.map { it.pointName }).distinct()
    return left.maxOf { l -> right.maxOf { r -> longestCommonSubstring(l, r) } }
}

/** 名称归一化后仍不同，才有合并的必要；公共片段长度是这里唯一的相似度证据。 */
fun longestCommonSubstring(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    var best = 0
    var prev = IntArray(b.length + 1)
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        for (j in 1..b.length) {
            if (a[i - 1] == b[j - 1]) {
                cur[j] = prev[j - 1] + 1
                if (cur[j] > best) best = cur[j]
            }
        }
        prev = cur
    }
    return best
}

private const val MAX_ALIAS_HOPS = 8
private const val DEFAULT_MIN_COMMON = 4
