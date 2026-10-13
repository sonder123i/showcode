package dev.showcode.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 快递 / 外卖分流。
 *
 * 前四条是用户真机截图里的原文（2026-10-13，「没认出来的取件短信」页整屏都是这四类）。
 * 它们结构上永远抽不出取件码——美团、蜂鸟用 App 或手机尾号开门——所以留在
 * 「你漏了取件码」那条列表里，就是一条永远清不掉的噪音。
 *
 * 后面的快递用例是刹车：分流的代价不对称，误判成外卖会把真取件码藏进折叠区，
 * 所以任何提高外卖召回的改动都必须先把这组压住。
 */
class MessageDomainTest {

    private fun check(expected: MessageDomain, body: String) =
        assertEquals(expected, MessageDomains.classify(body))

    // ---------- 用户真机原文，必须判成外卖 ----------

    @Test
    fun `美团入柜超时提醒判成外卖`() {
        check(
            MessageDomain.FOOD,
            "【美团】您的美团订单入柜已超过1小时，超过12小时外卖将被清理，为避免损失请及时取件",
        )
    }

    @Test
    fun `蜂鸟餐放柜里判成外卖`() {
        check(
            MessageDomain.FOOD,
            "【蜂鸟配送】您的订单2分钟后送达您的餐都放在柜里，放在淘宝闪购或美团柜子里，" +
                "找不到餐就找穿蓝色马甲的人员，如不能解决请联系客服，如有问题请去订单详情页联系骑手.谢谢～",
        )
    }

    @Test
    fun `美团外卖柜带四位取件号仍然判成外卖`() {
        // 这条是唯一抽得出码的（7902），但它落在 POINT_NAME_UNCONFIRMED，
        // 而且柜子是外卖柜——分流只看域，不看有没有码。
        check(
            MessageDomain.FOOD,
            "【美团】您的外卖已放在南昌工学院31栋门口3柜27格口，点击 mt.cn/nlJsGMz 或使用7902取件 拒收请回复R",
        )
    }

    @Test
    fun `蜂鸟楼下外卖柜判成外卖`() {
        check(
            MessageDomain.FOOD,
            "【蜂鸟配送】您的订单已经送达您的外卖已经送达楼下白色淘宝外卖柜，或货架上，" +
                "请认真看一下送达照片！！！请及时下楼取餐，如有问题请去订单详情页联系骑手.谢谢～",
        )
    }

    // ---------- 刹车：带品牌词的快递短信不得被藏起来 ----------

    @Test
    fun `普通驿站与快递柜短信判成快递`() {
        check(
            MessageDomain.PARCEL,
            "【菜鸟驿站】您的包裹已入库，取件码 5-1-2277，请前往南昌工学院菜鸟驿站取件",
        )
        check(MessageDomain.PARCEL, "【丰巢】您有一个快递已入柜，取件码6-1-0088，请及时取件。")
        check(MessageDomain.PARCEL, "【极兔速递】您的快件已送达，请凭6-99-226到极兔领取")
    }

    @Test
    fun `美团优选自提订单是货不是外卖`() {
        // 品牌词命中「美团」，但「自提」是外卖侧不会写的词，快递硬锚优先。
        check(MessageDomain.PARCEL, "【美团优选】您的订单已到自提点，请凭取货码尽快前往提取")
    }

    @Test
    fun `没有品牌词的通用入柜短信默认快递`() {
        check(MessageDomain.PARCEL, "【10690123】您的物品已放入智能柜，取件码 8-9-0765")
    }
}
