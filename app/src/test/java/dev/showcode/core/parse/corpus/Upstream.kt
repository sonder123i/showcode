package dev.showcode.core.parse.corpus

import dev.showcode.core.PointKind
import dev.showcode.core.parse.UnrecognizedReason

/**
 * 来自在位者 `shareven/parcel` issue 的**真实用户原文**（已按 CorpusCase 的注释做脱敏轮换）。
 *
 * 这一组的价值不在于「我们也会不会错」，而在于：它们每一个都是真人报过、
 * 并且当场让那个人白跑或找不到件的写法。棘轮要往上抬，优先抬这一组。
 *
 * ⚠️ 2026-10-12 之后，这里一半的条目期望值是 REJECTED(POINT_NAME_UNCONFIRMED)。
 * 这不是退步：码认出来了、但说不出它属于哪个取件点时，我们不再退化成「菜鸟驿站」这种
 * 看着正常的卡（那会把不同小区合成一张），而是连码一起送进未识别等人补一个名字。
 * 每条都用 hintCodes 钉住「拒绝必须把码带走」。
 */
object Upstream {

    val CASES: List<CorpusCase> = listOf(
        CorpusCase(
            id = "up-17-a",
            origin = "issue #17 · 多多代收点「到店输入 NNNNN」型，没有任何取件码锚定词",
            body = "【多多代收点】尾号51330包裹已到示例河谷1-09门面房菜鸟驿站，到店输入02996或扫码亮灯取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "02996",
                pointName = "示例河谷1-09门面房菜鸟驿站",
                kind = PointKind.STATION,
            ),
        ),
        CorpusCase(
            id = "up-17-b",
            origin = "issue #17 · 「领取中通6120的快件」——码跟在快递公司名后面，真值本身有争议；" +
                "而且整条只有品牌名，认不出具体门店",
            body = "【菜鸟】您好，请前往示例驿站幸福谷08号商铺示例驿站，领取中通6120的快件",
            authoritative = false,
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("6120"),
            ),
        ),
        CorpusCase(
            id = "up-29-a",
            origin = "issue #29 · 从拼多多复制的粘贴式文本：换行 + 竖线分隔，没有短信动词。" +
                "门店名在竖线后面，信息是齐的，不该送确认",
            body = "取件码 H7-05528\n韵达快递超市代收点 | 示例红波精品超市",
            expect = Expect(
                tier = Tier.PARSED,
                code = "H7-05528",
                pointName = "示例红波精品超市",
                kind = PointKind.SELFPICK,
            ),
        ),
        CorpusCase(
            id = "up-29-b",
            origin = "issue #29 · 柜号与码粘连「取件码 4号柜719077」，在位者报「100%解析失败」。" +
                "类型期望是代收超市（SELFPICK）：现在的解析器只看名字、不看竖线左半截，" +
                "所以这条会诚实地记一个字段漏——别把它改成 null 藏起来",
            body = "取件码 4号柜719077\n兔喜快递超市代收点 | 示例农贸市场斜对面中通快递",
            expect = Expect(
                tier = Tier.PARSED,
                code = "719077",
                lockerNo = "4号柜",
                kind = PointKind.SELFPICK,
            ),
        ),
        CorpusCase(
            id = "up-35",
            origin = "issue #35 · 「请用T8-99到人工货架取包裹」，并带一个运单尾号诱饵。" +
                "正文里的点位是个占位符「【示例地址】」，那不是名字",
            body = "【递示例】您的快递:*30275已到【示例地址】，请用T8-99到人工货架取包裹，咨询驿站工作人员",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("T8-99"),
            ),
        ),
        CorpusCase(
            id = "up-43-a",
            origin = "issue #43 · 在位者把柜格标记 H09 当成了取件码。我们的码是对的，" +
                "但智柜品牌不在兜底表里，退成了通用「取件点」——所以这条该等人确认",
            body = "【示例智柜】快递员提醒您，请凭取件码370999到9号柜圆通快递H09取您的快递",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("370999"),
            ),
        ),
        CorpusCase(
            id = "up-43-b",
            origin = "issue #43 · 「请凭6-99-226到极兔领取」——重构前整条无痕蒸发；" +
                "现在进了门，但只有品牌名，送去确认",
            body = "【极兔速递】请凭6-99-226到极兔领取运单尾号8900包裹",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("6-99-226"),
            ),
        ),
        CorpusCase(
            id = "up-53",
            origin = "issue #53 · 「已到 + 整段收货地址」：以前会把地址当点名生成一张卡，" +
                "每条新地址一张新卡。现在认出来那是引号包裹的句子尾巴，送确认",
            body = "【示例驿站】取货码fbxr-0866，您有包裹已到示例区示例街道幸福路18号小区9号楼703室“圆通速递”公众号",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("fbxr-0866"),
            ),
        ),
        CorpusCase(
            id = "up-56",
            origin = "issue #56 · 汉字柜号「五号柜」已能认；但这条只写了丰巢，没写是哪台柜机，" +
                "所以不能替用户断定他在小区门口那台",
            body = "【丰巢】取件码9-4-5512，请到五号柜取件，18小时内免费保管，超时将收取保管费",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("9-4-5512"),
            ),
        ),
        CorpusCase(
            id = "up-tx-ruixin",
            origin = "补写 · 兔喜「已入库」+「数字密码」：重构前连门都进不了；" +
                "现在码认得出来，兔喜却不在兜底表里 → 送确认",
            body = "【兔喜】您的快件已入库，请凭数字密码 8-9-0765 到柜领取",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("8-9-0765"),
            ),
        ),
        CorpusCase(
            id = "up-tx-ems",
            origin = "补写 · 邮政包裹到局自取：点名从正文抽得出、还带营业时间段，是今天能全自动的一类",
            body = "【中国邮政】您的包裹已到幸福路邮政支局，请凭取件码 61092 到窗口领取，营业时间08:30-17:30",
            expect = Expect(
                tier = Tier.PARSED,
                code = "61092",
                pointName = "幸福路邮政支局",
                kind = PointKind.STATION,
                closesAtMinute = 17 * 60 + 30,
            ),
        ),
        CorpusCase(
            id = "real-2026-10-13-jitu-dao-qu",
            origin = "用户真机样本 2026-10-13 · 「凭码**到**X**取**尾号…」：动词是介词「到」，" +
                "不在那 12 个到达类动词里。**现有 35 条语料一条都没有这种句式**——" +
                "类别词回吃这条规则在旧语料上零收益，只有这条新样本能证明它值这个改动",
            body = "【极兔速递】凭3-3-10382到示例学院多多驿站取尾号8539包裹",
            expect = Expect(
                tier = Tier.PARSED,
                code = "3-3-10382",
                pointName = "示例学院多多驿站",
                kind = PointKind.STATION,
                forbiddenCodes = listOf("8539"),
                forbiddenInPointName = listOf("尾号", "包裹", "取"),
            ),
        ),
        CorpusCase(
            id = "real-2026-10-13-sto-code-at-tail",
            origin = "用户真机样本 2026-10-13 · 码在句尾、前面只有「取件」二字（锚词表里只有「取件码」），" +
                "15 位运单号是诱饵。点名现在靠回吃能出，**码还抽不出来**——" +
                "真值没有争议，是解析器治不好，所以标存疑占位，不许进棘轮分母假装通过",
            body = "【申通快递】您的包裹777411959127370已在示例学院多多驿站滞留1天未取，请尽快取件5-4-23010",
            authoritative = false,
            expect = Expect(
                tier = Tier.PARSED,
                code = "5-4-23010",
                pointName = "示例学院多多驿站",
                kind = PointKind.STATION,
                forbiddenCodes = listOf("777411959127370", "8539"),
            ),
        ),
        CorpusCase(
            id = "up-16",
            origin = "issue #16 · 「凭取件码X至火星1号丰巢柜取件」+ 一条短链。" +
                "在位者是被链接里的字母 token 搞坏的；我们这边闭集补上「品牌+柜」之后点名能出，" +
                "而链接那两个 token 必须一个都不许变成件",
            body = "【丰巢】凭取件码11451419至火星1号丰巢柜取件。快递员及畅存规则p.fcbox.com/bIqF6",
            expect = Expect(
                tier = Tier.PARSED,
                code = "11451419",
                pointName = "火星1号丰巢柜",
                kind = PointKind.LOCKER,
                freeUntilHours = 18L,
                forbiddenCodes = listOf("bIqF6", "fcbox", "com"),
            ),
        ),
        CorpusCase(
            id = "up-18",
            origin = "issue #18 · 「凭39-5-11463到快递服务中心取件」：码是确定的，" +
                "但正文里**没有任何门店标识**。「服务中心」因此被明确排除在类别词闭集之外——" +
                "收了就是替用户猜全校到底有几个点。这一条是天花板那一档，只考核码有没有被带走",
            body = "【近邻宝】凭39-5-11463到快递服务中心取件",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("39-5-11463"),
            ),
        ),
        CorpusCase(
            id = "up-48",
            origin = "issue #48 · 「已到 + 一整段相对位置」：抽出来的是「邮局斜对面示例大酒店隔壁」，" +
                "不是店名。**判它可用**：措辞变化最多把同一个点分裂成两张卡，而首页的合并" +
                "（预览 + 5 秒撤销 + 别名登记）就是为这件事准备的；反过来判脏会让件掉进未识别列表，" +
                "用户根本不会发现——那是无痕蒸发，比分裂严重。尾号 0627 必须不许成码",
            body = "【快宝驿站】您尾号0627包裹已到邮局斜对面示例大酒店隔壁，请凭取货码13-72来取",
            expect = Expect(
                tier = Tier.PARSED,
                code = "13-72",
                pointName = "邮局斜对面示例大酒店隔壁",
                kind = PointKind.STATION,
                forbiddenCodes = listOf("0627"),
            ),
        ),
    )
}
