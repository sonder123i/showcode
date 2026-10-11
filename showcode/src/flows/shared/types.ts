// 跨 flow 共用类型定义
// 来源：spark-output/context/frame.json (design_decisions) + edge.json (状态定义)

/**
 * 三态时效（用户 2026-10-11 拍定为三态，不是四态）。
 * 紧迫度梯度不靠第四个色阶表达，而靠 deadlineLabel 里的倒计时数字连续变化承载。
 * 见 frame.json → design_decisions.urgency_thresholds.urgency_gradient
 */
export type UrgencyLevel = 'safe' | 'due-soon' | 'at-risk'

/** 代取件是一等类别（journey finding 5），不是附注 */
export type ParcelOwner = 'self' | 'family'

export type PickupPointKind = 'station' | 'locker' | 'selfpick'

export interface Parcel {
  id: string
  /** 取件码，等宽字体展示。真实格式如 "8-3-1024" */
  code: string
  /**
   * 柜号 / 货架号。可能缺失（部分驿站短信不含）。
   * 缺失时现场模式必须「整个隐藏柜号区 + 取件码变得更大」，
   * 不留空位、不显示 "--"。原则：不是留白，是重排。
   * 见 edge.json → boundary-null
   */
  lockerNo?: string
  carrier: string
  pickupPointId: string
  owner: ParcelOwner
  /** 归属人显示名，owner === 'family' 时使用，用户可改（如「帮妈妈取」） */
  ownerLabel?: string
  urgency: UrgencyLevel
  /**
   * 临期态的倒计时文本。三态下这是唯一的紧迫度梯度载体，
   * 必须随时间连续变化（"剩 4小时12分" → "剩 1小时52分" → "剩 38分"）
   */
  deadlineLabel?: string
  /**
   * 取件码已存放天数。> 1 时现场模式提示「可能已失效」但**不阻断出示**。
   * 见 edge.json → error-stale-code
   */
  staleDays?: number
  picked?: boolean
}

export interface PickupPoint {
  id: string
  /** 完整名称。现场模式必须完整显示（用户靠它确认「我来对地方了」），首页可截断 */
  name: string
  /** 组件分节用的短名。2×2 组件空间有限 */
  shortName: string
  kind: PickupPointKind
  /**
   * 该点最近几次的原始名称样本。
   * 合并预览时展示给用户，让他用证据判断而不是凭记忆。
   * 见 edge.json → error-wrong-merge，story-6 AC#1
   */
  aliases: string[]
  /** 驿站关门时间文本。locker 类型没有（改用丰巢倒计时） */
  closingLabel?: string
}

/**
 * 桌面组件 4 形态（edge.json → s3-widget.widget_forms）
 * A 未绑定·空 / B 通用·按取件点分节(MVP默认) / C 绑定单点 / D 绑定点已清空
 */
export type WidgetForm = 'A' | 'B' | 'C' | 'D'

export const URGENCY_COLOR: Record<UrgencyLevel, string> = {
  safe: 'var(--adm-color-success)',
  'due-soon': 'var(--adm-color-warning)',
  'at-risk': 'var(--adm-color-danger)',
}

export const URGENCY_LABEL: Record<UrgencyLevel, string> = {
  safe: '还早',
  'due-soon': '临期',
  'at-risk': '过期风险',
}

export const POINT_KIND_LABEL: Record<PickupPointKind, string> = {
  station: '驿站',
  locker: '快递柜',
  selfpick: '自提点',
}
