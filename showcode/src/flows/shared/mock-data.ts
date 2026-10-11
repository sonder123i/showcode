import type { Parcel, PickupPoint } from './types'

// Mock 数据。真实感中文，用于设计验证。
// 覆盖 edge.json 里全部需要目视确认的边界态：
//   boundary-null（无柜号）· error-stale-code（3天前的码）·
//   boundary-daqu（代取件）· boundary-long-text（超长驿站名）·
//   boundary-overflow（双十一 8 件）

export const PICKUP_POINTS: PickupPoint[] = [
  {
    id: 'fengchao-3',
    name: '丰巢快递柜(3号楼下)',
    shortName: '丰巢柜·3号楼',
    kind: 'locker',
    aliases: ['丰巢快递柜', '丰巢(3号楼)', 'FC3号楼柜'],
  },
  {
    id: 'cainiao-dongmen',
    name: '菜鸟驿站(阳光花园小区东门店)',
    shortName: '菜鸟驿站·东门',
    kind: 'station',
    aliases: ['菜鸟驿站', '阳光花园东门便利店', '菜鸟驿站(东门店)'],
    closingLabel: '21:00 关门',
  },
  {
    id: 'jd-bianlidian',
    name: '京东自提点(好邻居便利店)',
    shortName: '京东·便利店',
    kind: 'selfpick',
    aliases: ['京东自提点', '好邻居便利店'],
    closingLabel: '22:30 关门',
  },
]

/**
 * 默认数据集：3 个取件点、6 件待取。
 * 故意让「阳光花园东门便利店」与「菜鸟驿站(阳光花园小区东门店)」并存于 aliases，
 * 用来演示保守归一化下同一取件点被拆成两张卡片的样子（story-6 的场景）。
 */
export const PARCELS: Parcel[] = [
  // ── 丰巢柜(3号楼下)：1 件，**已超时**。规则已核实：免费 18h，之后 0.5元/12h，上限 3元
  //    刻意设成 at-risk 且件数最少——这样「按紧急度排」把它放第一，
  //    「按件数排」把它放最后。两种排序产出不同序列，才能暴露排序 bug。
  {
    id: 'p1',
    code: '8-3-1024',
    lockerNo: 'A12',
    carrier: '顺丰速运',
    pickupPointId: 'fengchao-3',
    owner: 'self',
    urgency: 'at-risk',
    deadlineLabel: '已超时 · 0.5 元/12小时（上限 3 元）',
  },

  // ── 菜鸟驿站(阳光花园小区东门店)：3 件
  {
    id: 'p2',
    code: '5-2-3391',
    lockerNo: '3排12号',
    carrier: '中通快递',
    pickupPointId: 'cainiao-dongmen',
    owner: 'self',
    urgency: 'safe',
  },
  {
    // boundary-null：短信不含货架号 → 现场模式柜号区整个消失、码变大
    id: 'p3',
    code: '5-2-3408',
    carrier: '圆通速递',
    pickupPointId: 'cainiao-dongmen',
    owner: 'self',
    urgency: 'safe',
  },
  {
    // error-stale-code：3 天前的码，可能已被驿站退回。
    // 驿站没有营业时间库（V2），临期只能靠点位上的 closingLabel 推算
    id: 'p4',
    code: '5-1-2277',
    lockerNo: '1排06号',
    carrier: '韵达快递',
    pickupPointId: 'cainiao-dongmen',
    owner: 'self',
    urgency: 'due-soon',
    deadlineLabel: '驿站 21:00 关门',
    staleDays: 3,
  },

  // ── 京东自提点(好邻居便利店)：2 件，其中 1 件代取
  {
    id: 'p5',
    code: 'JD7739201',
    lockerNo: '货架 B-4',
    carrier: '京东物流',
    pickupPointId: 'jd-bianlidian',
    owner: 'self',
    urgency: 'safe',
  },
  {
    // boundary-daqu：代取件，来自妈妈经微信转发 → 通知解析
    id: 'p6',
    code: 'JD7739455',
    lockerNo: '货架 B-7',
    carrier: '京东物流',
    pickupPointId: 'jd-bianlidian',
    owner: 'family',
    ownerLabel: '帮妈妈取',
    urgency: 'due-soon',
    deadlineLabel: '21:30 后可能无人值守',
  },
]

/**
 * 双十一溢出数据集：单取件点 8 件。
 * 用于 Screen 4 平铺总览——8 件时用户不想滑 8 次，需要一屏看完自己挑。
 * 这是「现场模式极低密度」双模哲学的必要例外。
 */
export const PARCELS_OVERFLOW: Parcel[] = Array.from({ length: 8 }, (_, i) => ({
  id: `ov-${i + 1}`,
  code: `6-4-${1000 + i * 37}`,
  lockerNo: `${2 + Math.floor(i / 4)}排${String((i % 4) * 3 + 5).padStart(2, '0')}号`,
  carrier: ['中通快递', '圆通速递', '韵达快递', '申通快递'][i % 4],
  pickupPointId: 'cainiao-dongmen',
  owner: 'self' as const,
  urgency: (i === 0 ? 'due-soon' : i === 5 ? 'at-risk' : 'safe') as Parcel['urgency'],
  deadlineLabel: i === 0 ? '剩 1小时52分' : i === 5 ? '已存放 2 天' : undefined,
  staleDays: i === 5 ? 2 : undefined,
}))

/** 该点无件（form-D / 现场模式 empty）。不能是空白屏——用户站在柜机前看到空白会恐慌 */
export const LAST_PICKUP_LABEL = '最近一次取件：昨天 18:32'

/** 权限健康告警文案（edge.json → permission-health-bar）。静默失效必须被主动告知 */
export const PERMISSION_ALERT = '取件码自动收录已暂停 · 通知监听被关闭'

/** 去开启的具体路径提示。H5 mockup 无法跳系统设置，必须至少给出路径 */
export const PERMISSION_FIX_PATH = '系统设置 → 应用与通知 → 通知使用权 → 开启「亮码」'

/** 未识别角标计数（edge.json → unrecognized-badge）。只在通用组件显示一次 */
export const UNRECOGNIZED_COUNT = 2

/**
 * 未识别短信原文（edge.json → s7-unrecognized 列表态）。
 * 真实在位者已知失败源：5G 消息 / 无发件人号码的网络短信、微信折叠通知。
 * 「报告给社区」默认不勾选——隐私红线。
 */
export const UNRECOGNIZED_SMS: Array<{ id: string; sender: string; body: string; reason: string }> = [
  {
    id: 'u1',
    sender: '1069xxxx',
    body: '【菜鸟驿站】您的包裹已到站，请在小程序内查看取件凭证，凭码取件。',
    reason: '正文里没有可识别的取件码，指向了小程序',
  },
  {
    id: 'u2',
    sender: '（无发件人号码）',
    body: '5G 消息：丰巢快递柜投递成功，点击卡片查看取件码',
    reason: '网络短信无发件人号码，在位者 README 亦承认此类识别不了',
  },
]

/**
 * 组件数据新鲜度（分钟）。> 120 时组件转琥珀「可能不是最新」。
 * 陈旧的码比没有码更危险——用户会拿着三天前的码去柜机前。
 */
export const WIDGET_AGE_MINUTES = 12
export const STALE_THRESHOLD_MINUTES = 120
