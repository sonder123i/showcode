package dev.showcode.core.parse.corpus

import dev.showcode.core.parse.UnrecognizedReason

/**
 * 负样本：不该变成一件待取快递的消息。
 *
 * 这一组是「放宽进门」那步的刹车。`PICKUP_SIGNAL` 之所以是硬闸门，
 * 是因为未识别列表一旦被营销/验证码填满，桌面角标就常年是个无意义的数字，
 * 用户会直接忽略它——那样整个「失败显性化」设计就废了。
 * 所以任何提高召回的改动，都必须同时把这组的数字压住。
 */
object Negatives {

    val CASES: List<CorpusCase> = listOf(
        CorpusCase(
            id = "neg-marketing",
            origin = "营销短信",
            body = "【天猫】双11狂欢节全场5折起，点击领取优惠券 http://tb.cn/x",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-tracking",
            origin = "物流轨迹（含「已到达」但不是取件）",
            body = "【顺丰速运】您的快件已到达杭州中转中心，正在派送中，请保持电话畅通。",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-signed",
            origin = "签收通知",
            body = "【京东物流】您的订单已签收，感谢使用京东，期待再次为您服务。",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-otp-fengchao",
            origin = "丰巢发的登录/操作验证码：品牌词过门，但没有取件语义",
            body = "【丰巢】您本次的验证码是610920，请勿泄露给他人。",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-otp-bank",
            origin = "银行验证码，无任何快递词",
            body = "【工商银行】您正在登录，验证码 610920，请勿泄露。",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-weak-anchor-needs-context",
            origin = "弱锚定必须有语境：「请使用」后面确实有数字，但整条没有取件语境——" +
                "收进来就是一件永远取不到的假快递",
            body = "【示例支付】请使用 889900 完成支付，请勿转发他人",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-courier-phone",
            origin = "派件员电话联系，正文里有一个 11 位手机号",
            body = "您的快递员正在派送，联系电话13800138000，请保持电话畅通",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-station-promo",
            origin = "驿站营销：带「驿站」所以能过门，但没有码",
            body = "【菜鸟驿站】您的驿站补贴已到账，点击领取88元优惠券",
            expect = Expect(tier = Tier.IGNORED),
        ),
        CorpusCase(
            id = "neg-code-expired",
            origin = "码已失效、要回 App 重新生成：这是「该给原因」而不是「该收进来」",
            body = "【丰巢】您的取件码已失效，请重新在App内生成",
            expect = Expect(tier = Tier.REJECTED, reason = UnrecognizedReason.CODE_LIVES_IN_APP),
        ),
    )
}
