package dev.showcode.core.parse.corpus

import dev.showcode.core.PointKind

/**
 * 来自在位者 `shareven/parcel` issue 的**真实用户原文**（已按 CorpusCase 的注释做脱敏轮换）。
 *
 * 这一组的价值不在于「我们也会不会错」，而在于：它们每一个都是真人报过、
 * 并且当场让那个人白跑或找不到件的写法。棘轮要往上抬，优先抬这一组。
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
            origin = "issue #17 · 「领取中通6120的快件」——码跟在快递公司名后面，真值本身有争议",
            body = "【菜鸟】您好，请前往示例驿站幸福谷08号商铺示例驿站，领取中通6120的快件",
            authoritative = false,
            expect = Expect(
                tier = Tier.PARSED,
                code = "6120",
                pointName = "幸福谷08号商铺示例驿站",
                kind = PointKind.STATION,
            ),
        ),
        CorpusCase(
            id = "up-29-a",
            origin = "issue #29 · 从拼多多复制的粘贴式文本：换行 + 竖线分隔，没有短信动词",
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
            origin = "issue #29 · 柜号与码粘连「取件码 4号柜719077」，在位者报「100%解析失败」",
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
            origin = "issue #35 · 「请用T8-99到人工货架取包裹」，并带一个运单尾号诱饵",
            body = "【递示例】您的快递:*30275已到【示例地址】，请用T8-99到人工货架取包裹，咨询驿站工作人员",
            expect = Expect(
                tier = Tier.PARSED,
                code = "T8-99",
                kind = PointKind.STATION,
            ),
        ),
        CorpusCase(
            id = "up-43-a",
            origin = "issue #43 · 在位者把柜格标记 H09 当成了取件码；我们要证明不会跟着错",
            body = "【示例智柜】快递员提醒您，请凭取件码370999到9号柜圆通快递H09取您的快递",
            expect = Expect(
                tier = Tier.PARSED,
                code = "370999",
                lockerNo = "9号柜",
                kind = PointKind.LOCKER,
                forbiddenInPointName = listOf("取件点"),
            ),
        ),
        CorpusCase(
            id = "up-43-b",
            origin = "issue #43 · 「请凭6-99-226到极兔领取」——今天连门都进不了，整条无痕蒸发",
            body = "【极兔速递】请凭6-99-226到极兔领取运单尾号8900包裹",
            expect = Expect(tier = Tier.PARSED, code = "6-99-226"),
        ),
        CorpusCase(
            id = "up-53",
            origin = "issue #53 · 「已到 + 整段收货地址」被当成取件点名，卡片名变成地址且每条新地址一张新卡",
            body = "【示例驿站】取货码fbxr-0866，您有包裹已到示例区示例街道幸福路18号小区9号楼703室“圆通速递”公众号",
            expect = Expect(
                tier = Tier.PARSED,
                code = "fbxr-0866",
                kind = PointKind.STATION,
                forbiddenInPointName = listOf("号楼", "公众号"),
            ),
        ),
        CorpusCase(
            id = "up-56",
            origin = "issue #56 · 汉字柜号「五号柜」，在位者同样只在阿拉伯数字时能认",
            body = "【丰巢】取件码9-4-5512，请到五号柜取件，18小时内免费保管，超时将收取保管费",
            expect = Expect(
                tier = Tier.PARSED,
                code = "9-4-5512",
                lockerNo = "五号柜",
                kind = PointKind.LOCKER,
                freeUntilHours = 18L,
            ),
        ),
        CorpusCase(
            id = "up-tx-ruixin",
            origin = "补写 · 兔喜「已入库」+「数字密码」：signal 词表是「已入柜」，一字之差整条无痕蒸发",
            body = "【兔喜】您的快件已入库，请凭数字密码 8-9-0765 到柜领取",
            expect = Expect(tier = Tier.PARSED, code = "8-9-0765"),
        ),
        CorpusCase(
            id = "up-tx-ems",
            origin = "补写 · 邮政包裹到局自取：有明确营业时间段，是今天也能全对的一类",
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
