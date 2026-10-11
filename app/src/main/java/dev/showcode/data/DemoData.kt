package dev.showcode.data

import dev.showcode.core.HOUR_MS
import dev.showcode.core.MINUTE_MS
import dev.showcode.core.DAY_MS
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.pointKey

/**
 * 演示态数据（edge s4-onsite.demo-mode / s2-home.empty-first-time）。
 *
 * 三条硬约束：
 * 1. **永不落库**。退出演示后不残留任何示例内容（story-1 AC3）。id 一律取负数，
 *    这样即使哪天有人误把演示件传进 repository，按 id 更新也匹配不到任何真实行。
 * 2. 时效**相对当前时刻**生成，不写死。演示的目的是让人看到那个巨大的取件码与
 *    三态配色；如果 21:00 之后打开演示全是「安全」，临期倒计时这个核心差异点就演示不到。
 * 3. 一份数据要覆盖 edge 点名的全部现场变体：无柜号重排、失效码、代取分区、8 件平铺。
 */
object DemoData {

    private const val FENGCHAO = "丰巢快递柜(3号楼下)"
    private const val CAINIAO = "菜鸟驿站(阳光花园小区东门店)"
    private const val JD = "京东自提点(好邻居便利店)"

    fun parcels(now: Long): List<Parcel> = listOf(
        // ── 丰巢柜：一件已超时（过期风险），一件剩 4 小时 12 分（临期，倒计时逐秒变化）
        demo(
            id = -1, code = "8-3-1024", lockerNo = "A12", carrier = "顺丰速运",
            pointName = FENGCHAO, kind = PointKind.LOCKER,
            receivedAt = now - 19 * HOUR_MS, freeUntilAt = now - HOUR_MS,
            raw = "【丰巢】您的顺丰速运快递已放入丰巢快递柜(3号楼下)，格口A12，取件码：8-3-1024，" +
                "18小时内免费保管，超时按0.5元/12小时计费，上限3元。",
        ),
        demo(
            id = -2, code = "6-2-0588", lockerNo = "B07", carrier = "京东物流",
            pointName = FENGCHAO, kind = PointKind.LOCKER,
            receivedAt = now - 14 * HOUR_MS, freeUntilAt = now + 4 * HOUR_MS + 12 * MINUTE_MS,
            raw = "【丰巢】您的京东物流快递已放入丰巢快递柜(3号楼下)，格口B07，取件码：6-2-0588，18小时内免费保管。",
        ),

        // ── 菜鸟驿站：5 件。够多到首页卡片要收起来（前 3 件 + 「+2」），
        //    也够多到现场模式必须给「平铺看全部」入口（>3 件）。
        demo(
            id = -3, code = "5-2-3391", lockerNo = "3排12号", carrier = "中通快递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - 5 * HOUR_MS, closesAtMinuteOfDay = 21 * 60,
            raw = "【菜鸟驿站】您的中通快递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-2-3391，货架3排12号。",
        ),
        // boundary-null：短信里就没有货架号 → 现场模式柜号区整块消失、取件码变大
        demo(
            id = -4, code = "5-2-3408", lockerNo = null, carrier = "圆通速递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - 3 * HOUR_MS, closesAtMinuteOfDay = 21 * 60,
            raw = "【菜鸟驿站】您的圆通速递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-2-3408，请及时领取。",
        ),
        // error-stale-code：3 天前的码，可能已被驿站退回 → 提示但绝不阻断出示
        demo(
            id = -5, code = "5-1-2277", lockerNo = "1排06号", carrier = "韵达快递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - 3 * DAY_MS - 2 * HOUR_MS, closesAtMinuteOfDay = 21 * 60,
            raw = "【菜鸟驿站】您的韵达快递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-1-2277，货架1排06号。",
        ),
        demo(
            id = -6, code = "5-3-4416", lockerNo = "2排03号", carrier = "申通快递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - HOUR_MS, closesAtMinuteOfDay = 21 * 60,
            raw = "【菜鸟驿站】您的申通快递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-3-4416，货架2排03号。",
        ),
        demo(
            id = -7, code = "5-3-4420", lockerNo = null, carrier = "极兔速递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - 30 * MINUTE_MS, closesAtMinuteOfDay = 21 * 60,
            raw = "【菜鸟驿站】您的极兔速递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-3-4420，请及时领取。",
        ),

        // ── 京东自提点：2 件，其中 1 件代取。
        //    boundary-daqu：轮播不跨组，在自己的码和家人的码之间迷失会直接抵消「找得准」。
        demo(
            id = -8, code = "JD7739201", lockerNo = "B-4", carrier = "京东物流",
            pointName = JD, kind = PointKind.SELFPICK,
            receivedAt = now - 6 * HOUR_MS, closesAtMinuteOfDay = 22 * 60 + 30,
            raw = "【京东物流】您的包裹已到达京东自提点(好邻居便利店)，取件码JD7739201，货架B-4，请及时领取。",
        ),
        demo(
            id = -9, code = "JD7739455", lockerNo = "B-7", carrier = "京东物流",
            pointName = JD, kind = PointKind.SELFPICK,
            owner = Owner.FAMILY,
            receivedAt = now - 2 * HOUR_MS, closesAtMinuteOfDay = 22 * 60 + 30,
            raw = "微信 妈妈：【京东物流】您的包裹已到达京东自提点(好邻居便利店)，取件码JD7739455，货架B-7。",
        ),
    )

    private fun demo(
        id: Long,
        code: String,
        lockerNo: String?,
        carrier: String,
        pointName: String,
        kind: PointKind,
        receivedAt: Long,
        freeUntilAt: Long? = null,
        closesAtMinuteOfDay: Int? = null,
        owner: Owner = Owner.SELF,
        raw: String,
    ) = Parcel(
        id = id,
        code = code,
        lockerNo = lockerNo,
        carrier = carrier,
        pointKey = pointKey(pointName),
        pointName = pointName,
        pointKind = kind,
        owner = owner,
        source = Source.DEMO,
        receivedAt = receivedAt,
        freeUntilAt = freeUntilAt,
        closesAtMinuteOfDay = closesAtMinuteOfDay,
        picked = false,
        pickedAt = null,
        raw = raw,
    )
}
