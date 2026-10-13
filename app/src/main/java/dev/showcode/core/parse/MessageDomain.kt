package dev.showcode.core.parse

/**
 * 一条消息属于哪个业务域：快递取件，还是外卖/跑腿入柜。
 *
 * 这不是审美问题，是未识别列表能不能守住承诺的问题。那条列表的原文是
 * 「宁可让你现在知道漏了，也不要让你站在柜机前才发现」——而外卖入柜短信
 * **结构上永远不会带取件码**（美团/蜂鸟用 App 或手机尾号开门），
 * 所以它一旦挤进这条列表，就是一条永远清不掉、又永远等不到识别成功的噪音。
 * 角标常年挂着无意义的数字，用户会连真漏掉的那条一起忽略掉。
 */
enum class MessageDomain { PARCEL, FOOD }

object MessageDomains {

    /**
     * 快递硬锚。命中任意一个就判 PARCEL，**优先于外卖词**——
     * 一条提到「美团」的快递短信仍然是快递短信，误判成外卖等于把真取件码藏起来，
     * 而漏判成外卖只是多一条待看的消息。两边代价不对称，所以偏向 PARCEL。
     */
    private val PARCEL_ANCHOR = Regex(
        "快递|驿站|包裹|快件|丰巢|菜鸟|兔喜|近邻宝|速递易|袋鼠|" +
            "极兔|中通|圆通|韵达|申通|顺丰|邮政|德邦|百世|" +
            "取件码|提货码|取货码|领取码|柜机码|代收点|收发室|自提"
    )

    private val FOOD_ANCHOR = Regex(
        "外卖|美团|蜂鸟|饿了么|京东到家|闪购|骑手|取餐|送餐|出餐|餐柜|饭菜"
    )

    fun classify(body: String): MessageDomain = when {
        PARCEL_ANCHOR.containsMatchIn(body) -> MessageDomain.PARCEL
        FOOD_ANCHOR.containsMatchIn(body) -> MessageDomain.FOOD
        else -> MessageDomain.PARCEL
    }
}
