package dev.showcode.core.parse.corpus

import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.parse.UnrecognizedReason

/**
 * 按各家官方文案手写的标准格式。
 *
 * 诚实边界：这一组和我在 `PickupCodeParserTest` 里已有的 9 条正样本是**同一类证据**——
 * 形态可信，覆盖率不可保证。它的用处是把「本来就该对」的那批变成防退化的地板，
 * 而不是证明我们能认全中国所有的短信写法（那需要真机语料）。
 */
object Formats {

    val CASES: List<CorpusCase> = listOf(
        CorpusCase(
            id = "fmt-cainiao",
            origin = "菜鸟驿站标准格式：门店名 + 货架「N排M号」 + 营业时间段",
            body = "【菜鸟驿站】您的中通快递包裹已到向阳小区菜鸟驿站，取件码5-2-7712，货架3排12号，营业时间08:00-20:00",
            expect = Expect(
                tier = Tier.PARSED,
                code = "5-2-7712",
                pointName = "向阳小区菜鸟驿站",
                lockerNo = "3排12号",
                kind = PointKind.STATION,
                closesAtMinute = 20 * 60,
            ),
        ),
        CorpusCase(
            id = "fmt-fengchao",
            origin = "丰巢标准格式：点名带分店、格口、显式免费时长",
            body = "【丰巢】您的顺丰速运快件已放入丰巢智能柜(示例花园3号楼柜)，格口A12，取件码8-3-7712，18小时内免费保管",
            expect = Expect(
                tier = Tier.PARSED,
                code = "8-3-7712",
                lockerNo = "A12",
                pointName = "丰巢智能柜(示例花园3号楼柜)",
                kind = PointKind.LOCKER,
                freeUntilHours = 18L,
            ),
        ),
        CorpusCase(
            id = "fmt-jd",
            origin = "京东自提点标准格式：字母数字混合码",
            body = "【京东物流】您的包裹已到达京东自提点(好邻居便利店)，取件码JD7739201，货架B-4，请及时领取",
            expect = Expect(
                tier = Tier.PARSED,
                code = "JD7739201",
                lockerNo = "B-4",
                pointName = "京东自提点(好邻居便利店)",
                kind = PointKind.SELFPICK,
            ),
        ),
        CorpusCase(
            id = "fmt-evening-21",
            origin = "驿站「21点前取件」——无歧义的那种，今天是对的",
            body = "【菜鸟驿站】包裹已到向阳小区菜鸟驿站，取件码7-1-3390，请在今日21点前取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "7-1-3390",
                pointName = "向阳小区菜鸟驿站",
                kind = PointKind.STATION,
                closesAtMinute = 21 * 60,
            ),
        ),
        CorpusCase(
            id = "fmt-pm",
            origin = "同一句式换成 12 小时制：「下午6点前」今天算成 06:00，件从早上起就全天标红",
            body = "【菜鸟驿站】包裹已到向阳小区菜鸟驿站，取件码7-1-3391，请在下午6点前取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "7-1-3391",
                pointName = "向阳小区菜鸟驿站",
                kind = PointKind.STATION,
                closesAtMinute = 18 * 60,
            ),
        ),
        CorpusCase(
            id = "fmt-free-hours-reversed",
            origin = "免费时长的反序词序「免费保管24小时」：抽不到就退默认 18，于是这个洞永远不会自己暴露",
            body = "【丰巢】快件已投递到丰巢柜，取件码226688，免费保管24小时",
            expect = Expect(
                tier = Tier.PARSED,
                code = "226688",
                kind = PointKind.LOCKER,
                freeUntilHours = 24L,
            ),
        ),
        CorpusCase(
            id = "fmt-two-parcels",
            origin = "一条短信两个件（同一次网购拆包 / 同一驿站批量到货）：第二件今天永久消失，且不落未识别",
            body = "【菜鸟驿站】您的两个包裹已到向阳小区菜鸟驿站，取件码7-1-3392、7-2-8801，请及时取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "7-1-3392",
                extraCodes = listOf("7-2-8801"),
                pointName = "向阳小区菜鸟驿站",
                kind = PointKind.STATION,
            ),
        ),
        CorpusCase(
            id = "fmt-two-parcels-repeated-anchor",
            origin = "多件的第二种写法：锚定词各写一次，而不是用顿号串起来",
            body = "【菜鸟驿站】您的两个包裹已到向阳小区菜鸟驿站，取件码7-1-3394，取件码7-2-8802，请及时取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "7-1-3394",
                extraCodes = listOf("7-2-8802"),
                kind = PointKind.STATION,
            ),
        ),
        CorpusCase(
            id = "fmt-locker-no-is-not-a-second-parcel",
            origin = "同形闸门：主码是纯数字时，顿号后面的 A12 是格口，不是一个件——" +
                "把它切成件就多一张永远取不到的假快递",
            body = "【菜鸟驿站】您的韵达包裹已到向阳小区菜鸟驿站，取件码512330、A12，请及时取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "512330",
                forbiddenCodes = listOf("A12"),
                kind = PointKind.STATION,
            ),
        ),
        CorpusCase(
            id = "fmt-hours-is-not-a-second-parcel",
            origin = "量词闸门：「，24小时内免费」里的 24 是保管时长不是取件码；这条同时钉住显式免费时长的解析",
            body = "【丰巢】快件已放入丰巢柜，取件码118077，24小时内免费保管",
            expect = Expect(
                tier = Tier.PARSED,
                code = "118077",
                forbiddenCodes = listOf("24"),
                kind = PointKind.LOCKER,
                freeUntilHours = 24L,
            ),
        ),
        CorpusCase(
            id = "fmt-notification",
            origin = "菜鸟 App 通知（换行分段的 bigText）。实测：动词后的 \\s* 会吃掉换行，" +
                "所以「点名独占一行」仍然抽得对——而且换行修复之后不再把「⏎取件码…」一起吞进来",
            sender = "com.cainiao.wireless",
            channel = Source.NOTIFICATION,
            body = "您有一件包裹已到达\n向阳小区菜鸟驿站(08号店)\n取件码 7-1-3393\n请及时取件",
            expect = Expect(
                tier = Tier.PARSED,
                code = "7-1-3393",
                pointName = "向阳小区菜鸟驿站(08号店)",
                kind = PointKind.STATION,
                forbiddenInPointName = listOf("取件码"),
            ),
        ),
        CorpusCase(
            id = "fmt-strong-anchor-beats-spam",
            origin = "强锚定优先于负向锚：真实取件短信会写「取件码…请勿泄露给他人」，" +
                "和验证码短信共用同一个负向词——谁先判，决定这件会不会凭空消失",
            body = "【丰巢】您的中通快件已放入丰巢智能柜(示例花园3号楼柜)，取件码8-3-7713，请勿泄露给他人",
            expect = Expect(
                tier = Tier.PARSED,
                code = "8-3-7713",
                kind = PointKind.LOCKER,
            ),
        ),
        CorpusCase(
            id = "fmt-code-in-app",
            origin = "码在 App 里，短信只有指引：必须给原因，不能静默",
            body = "【菜鸟】您的包裹已到站，请打开菜鸟App查看取件码",
            expect = Expect(tier = Tier.REJECTED, reason = UnrecognizedReason.CODE_LIVES_IN_APP),
        ),
        CorpusCase(
            id = "fmt-5g-no-sender",
            origin = "5G 网络短信没有发件人号码",
            sender = "",
            body = "5G消息：丰巢快递柜投递成功，点击卡片查看取件码",
            expect = Expect(tier = Tier.REJECTED, reason = UnrecognizedReason.NO_SENDER_NUMBER),
        ),
    )
}
