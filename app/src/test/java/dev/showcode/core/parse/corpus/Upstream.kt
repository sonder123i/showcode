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
                "码认得出来，但下一行到底是不是取件点，我们不敢替用户决定",
            body = "取件码 H7-05528\n韵达快递超市代收点 | 示例红波精品超市",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("H7-05528"),
            ),
        ),
        CorpusCase(
            id = "up-29-b",
            origin = "issue #29 · 柜号与码粘连「取件码 4号柜719077」，在位者报「100%解析失败」。" +
                "粘连已经能拆开，但取件点仍然认不出",
            body = "取件码 4号柜719077\n兔喜快递超市代收点 | 示例农贸市场斜对面中通快递",
            expect = Expect(
                tier = Tier.REJECTED,
                reason = UnrecognizedReason.POINT_NAME_UNCONFIRMED,
                hintCodes = listOf("719077"),
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
    )
}
