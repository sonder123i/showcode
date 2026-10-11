// =============================================
// FLOW: On-site Present（现场出示，6 screens）
// Scenario: Productivity（主参照 Flow 3 日视图任务管理）+ Consumer Finance（反向参照 Balance Concealment）
// Platform: H5 (Ant Design Mobile)
// ---------------------------------------------
// ⚠️ 本文件是**设计验证 mockup**，不是实现。真实 App 是 Kotlin + Compose 安卓原生。
//    目的：在真实手机屏幕上验证户外强光 / 单手 / 戴手套 / 屏幕有水 环境下的
//    字号、对比度、触控区尺寸。这些用文字描述永远答不了。
//
// Other flow 申报（与 Productivity Flow 3 的 5 个关键差异）：
//   1. 使用环境相反 → 反转信息密度决策（Flow3 高密度时间轴 / 本流程极低密度单码）
//   2. 核心容器维度不同（Flow3 时间轴 / 本流程按取件点分节的空间维度）
//   3. 完成操作后果不同（Flow3 原地划线留在视图 / 本流程该件消失并自动滑到下一件）
//   4. 入口不在 App 内（桌面组件某一分节，点击即携带「我在哪个点」的上下文）
//   5. 完全没有添加操作（数据全部被动解析，整套 FAB/Sheet/键盘避让体系不适用）
//
// 反向参照 Consumer Finance：金融场景要求遮蔽余额（公共场所隐私），
// 本流程**恰恰相反**——取件码要最大可见，因为用户就是要给柜机和店员看。
// =============================================

import { useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import {
  NavBar, Button, Grid, NoticeBar, Toast, SafeArea,
  List, PullToRefresh, Tag, Popup,
} from 'antd-mobile'
import {
  LeftOutline, ExclamationTriangleOutline, CheckOutline, ClockCircleOutline,
  TeamOutline, LoopOutline, RightOutline,
} from 'antd-mobile-icons'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import type { Parcel, UrgencyLevel } from '../shared/types'
import { URGENCY_COLOR, URGENCY_LABEL, POINT_KIND_LABEL } from '../shared/types'
import {
  PICKUP_POINTS, PARCELS, PARCELS_OVERFLOW, LAST_PICKUP_LABEL,
  PERMISSION_ALERT, PERMISSION_FIX_PATH, UNRECOGNIZED_COUNT, UNRECOGNIZED_SMS,
  WIDGET_AGE_MINUTES, STALE_THRESHOLD_MINUTES,
} from '../shared/mock-data'

// ─────────────────────────────────────────────
// 三态排序映射（锁定决策：三态驱动默认排序，不是时间、不是件数）
// 模块级唯一来源——此前 Screen1 与 Screen6 各写一套，
// 导致首页排序实现反了（Check finding M-r2-1）。
// ─────────────────────────────────────────────
const URGENCY_ORDER: Record<UrgencyLevel, number> = { 'at-risk': 0, 'due-soon': 1, safe: 2 }

/** 取一个点的紧急度代表：该点所有待取件中最高的一档 */
function worstUrgency(items: Parcel[]): UrgencyLevel {
  return items.reduce<UrgencyLevel>(
    (acc, x) => (URGENCY_ORDER[x.urgency] < URGENCY_ORDER[acc] ? x.urgency : acc),
    'safe',
  )
}

// ─────────────────────────────────────────────
// 共用：临期倒计时实时递减
// 三态决策的实现载体：紧迫度不靠第四个色阶，靠这个数字连续变化
// ─────────────────────────────────────────────
function useTickingMinutes(initialMinutes: number, running: boolean) {
  const [secondsLeft, setSecondsLeft] = useState(initialMinutes * 60)
  useEffect(() => {
    if (!running) return
    const t = setInterval(() => setSecondsLeft((s) => (s > 0 ? s - 1 : 0)), 1000)
    return () => clearInterval(t)
  }, [running])
  const h = Math.floor(secondsLeft / 3600)
  const m = Math.floor((secondsLeft % 3600) / 60)
  const s = secondsLeft % 60
  return h > 0 ? `剩 ${h}小时${String(m).padStart(2, '0')}分` : `剩 ${m}分${String(s).padStart(2, '0')}秒`
}

/**
 * 取件码字号。无柜号时放大 15%——不是留白，是重排。
 *
 * 不变量（Check finding M-r2-3）：**码宽必须 ≤ 可用宽度**。
 * 等宽字符宽约 0.6em；两侧内边距共 40px 在 320px 视口占掉 12.5%，
 * 故上限取 140/len vw。这样 8 字符码在 320px 下自动缩到 56px（269px ≤ 280px），
 * 在 390px 下仍走 72px 上限。iPhone SE 与大量低端安卓机就是 320px。
 */
function codeFontSize(code: string, hasLocker: boolean) {
  const base = code.length > 8 ? 54 : code.length > 6 ? 64 : 72
  const px = hasLocker ? base : Math.round(base * 1.15)
  return `min(${px}px, ${(140 / code.length).toFixed(1)}vw)`
}

// ─────────────────────────────────────────────
// 现场模式的核心渲染体。
// 导出给 onboarding flow 的演示屏复用——同一份取件码渲染逻辑，
// 避免两处实现走形（字号不变量、无柜号重排、时效渐变都在这里面）。
// ─────────────────────────────────────────────
export function OnsiteCodeBody({ parcel, tickingLabel, lockerLabel }: {
  parcel: Parcel; tickingLabel?: string; lockerLabel?: string
}) {
  const hasLocker = Boolean(parcel.lockerNo)
  return (
    <div style={{
      flex: 1, display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      padding: '0 20px', width: '100%', boxSizing: 'border-box',
    }}>
      {/* 快递公司：最低视觉权重，只是让用户确认「是这个包裹」 */}
      <div style={{ fontSize: 15, color: 'var(--onsite-ink)', opacity: 0.55, marginBottom: 16 }}>
        {parcel.carrier}
      </div>

      {/* 取件码：整个产品的灵魂。等宽 + 超大 + 纯黑，为户外强光优化 */}
      <div
        onContextMenu={(e) => e.preventDefault()}
        style={{
          fontFamily: 'var(--onsite-mono)',
          fontSize: codeFontSize(parcel.code, hasLocker),
          lineHeight: 1.05,
          fontWeight: 700,
          letterSpacing: '0.02em',
          color: 'var(--onsite-ink)',
          fontVariantNumeric: 'tabular-nums',
          textAlign: 'center',
          wordBreak: 'break-all',
          // long-press 复制（对应在位者 issue #52 的诉求）
          WebkitUserSelect: 'all',
          userSelect: 'all',
        }}
      >
        {parcel.code}
      </div>
      <div style={{ fontSize: 12, color: 'var(--onsite-ink)', opacity: 0.55, marginTop: 8 }}>
        长按可复制
      </div>

      {/* 柜号紧贴码下方。缺失时整块消失、码变大——不留空位、不显示 "--" */}
      {hasLocker && (
        <div style={{
          marginTop: 28, padding: '10px 24px',
          border: '2px solid var(--onsite-ink)', borderRadius: 12,
          display: 'flex', alignItems: 'baseline', gap: 10,
        }}>
          <span style={{ fontSize: 14, opacity: 0.6 }}>{lockerLabel ?? '格口'}</span>
          <span style={{
            fontFamily: 'var(--onsite-mono)', fontSize: 34, fontWeight: 700,
            fontVariantNumeric: 'tabular-nums', lineHeight: 1,
          }}>
            {parcel.lockerNo}
          </span>
        </div>
      )}

      {/* 时效：三态下唯一的颜色。小字、低位，不抢码的注意力 */}
      {(tickingLabel ?? parcel.deadlineLabel) && (
        <div style={{
          marginTop: 24, display: 'flex', alignItems: 'center', gap: 6,
          fontSize: 16, fontWeight: 600,
          color: parcel.urgency === 'safe' ? 'var(--onsite-ink)' : URGENCY_COLOR[parcel.urgency],
          opacity: parcel.urgency === 'safe' ? 0.5 : 1,
          fontVariantNumeric: 'tabular-nums',
        }}>
          <ClockCircleOutline fontSize={16} />
          {tickingLabel ?? parcel.deadlineLabel}
        </div>
      )}
    </div>
  )
}

/** 现场模式顶部信息条。决策 1：权限告警并进这一行，不新增视觉层 */
function OnsiteTopBar({ pointName, alert, onBack, onAlert }: {
  pointName: string; alert?: boolean; onBack: () => void; onAlert: () => void
}) {
  return (
    <NavBar
      backArrow={<LeftOutline fontSize={22} />}
      onBack={onBack}
      style={{
        '--height': '52px',
        borderBottom: '1px solid rgba(0,0,0,0.06)',
        background: 'var(--onsite-surface)',
      } as CSSProperties}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'center' }}>
        <span style={{
          fontSize: 15, fontWeight: 600, color: 'var(--onsite-ink)',
          maxWidth: 190, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>
          {pointName}
        </span>
        {/* 异常时才有这个图标，不占额外高度 */}
        {alert && (
          <span
            onClick={onAlert}
            style={{
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 28, height: 28, borderRadius: 14, flexShrink: 0,
              color: 'var(--adm-color-warning)', cursor: 'pointer',
            }}
            aria-label="自动收录已暂停，点此查看详情"
          >
            <ExclamationTriangleOutline fontSize={19} />
          </span>
        )}
      </div>
    </NavBar>
  )
}

/* ================================================
   FLOW: On-site Present
   SCREEN 1 of 6: Desktop Widget（桌面组件 · 形态 B）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  App 外 · 用户解锁手机后看到桌面
   EXIT:   tap 某个取件点分节 → SCREEN 2: On-site Main（1 次点击，可度量成功标准）
   BRANCH: tap 组件标题/空白 → SCREEN 6: Home（管理模式）
   BRANCH: tap 未识别角标 → 未识别收容页（flow 外）
   -----------------------------------------------
   STATE: form-A — 无任何数据，「在等第一条取件短信」+ 看演示入口
   STATE: form-B — 默认。按取件点分节，最多 2 节 + 「+N 个点」
   STATE: form-C — 已绑定单点，显示前 2-3 个码（用户升级后）
   STATE: form-D — 绑定点已清空，低对比度「✓ 无待取」
   STATE: unrecognized-badge — 有未识别短信（只在通用组件显示一次）
   STATE: stale-freshness — 数据超过 2 小时，转琥珀「可能不是最新」
   ================================================ */
export function Screen1_Widget() {
  const navigate = useNavigate()
  const [variant, setVariant] = useState<'B' | 'A' | 'C' | 'D'>('B')
  const [stale, setStale] = useState(false)
  const [unrecOpen, setUnrecOpen] = useState(false)

  // 按取件点分组，紧急度排序（三态驱动，不是时间、不是件数）
  const sections = useMemo(() => {
    return PICKUP_POINTS
      .map((p) => {
        const items = PARCELS.filter((x) => x.pickupPointId === p.id && !x.picked)
        return { point: p, items, worst: worstUrgency(items) }
      })
      .filter((s) => s.items.length > 0)
      .sort((a, b) => URGENCY_ORDER[a.worst] - URGENCY_ORDER[b.worst])
  }, [])

  const shown = sections.slice(0, 2)
  const rest = sections.length - shown.length
  const ticking = useTickingMinutes(252, variant === 'B' || variant === 'C')
  // 形态 C 的演示对象：菜鸟驿站 3 件（其中 1 件无柜号，可顺带验证重排规则）
  const bound = sections.find((s) => s.point.id === 'cainiao-dongmen') ?? sections[0]
  const isStale = stale || WIDGET_AGE_MINUTES > STALE_THRESHOLD_MINUTES

  return (
    <div style={{
      minHeight: '100vh', boxSizing: 'border-box', padding: '48px 16px 24px',
      // 模拟桌面壁纸，让这一屏读起来是「组件」而不是「App 内屏」。
      // 硬编码豁免：这是评审载体的背景，不是产品 UI。
      background: 'linear-gradient(160deg, #2b3a55 0%, #4a5d7a 55%, #6b7f9e 100%)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16,
    }}>
      <div style={{ color: 'var(--on-dark)', opacity: 0.75, fontSize: 12, textAlign: 'center', lineHeight: 1.6 }}>
        SCREEN 1 · 桌面组件（形态 {variant}）<br />
        这是模拟桌面，不是 App 内屏
      </div>

      {/* 2×2 组件本体。尺寸刻意压在 176×176，验证「不占太大位置」的约束 */}
      <div style={{
        width: 176, height: 176, borderRadius: 20, padding: 12, boxSizing: 'border-box',
        background: 'var(--onsite-surface)',
        boxShadow: '0 4px 20px rgba(0,0,0,0.28)',
        display: 'flex', flexDirection: 'column', gap: 6, overflow: 'hidden',
      }}>
        {/* 标题行：tap 这里进 App 首页 */}
        <div
          onClick={() => navigate('/home')}
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            cursor: 'pointer', minHeight: 20, gap: 4,
          }}
        >
          <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--onsite-ink)', opacity: 0.5, flexShrink: 0 }}>
            待取件
          </span>
          {/* stale-freshness（M-r2-5 修复）：陈旧的码比没有码更危险，必须条件渲染出来 */}
          {isStale && (
            <span style={{
              fontSize: 9, fontWeight: 700, lineHeight: 1,
              color: 'var(--adm-color-warning)', border: '1px solid var(--adm-color-warning)',
              borderRadius: 6, padding: '2px 4px', flexShrink: 0,
            }}>
              可能不是最新
            </span>
          )}
          {/* unrecognized-badge（B-r2-1 修复）：角标必须可点。
              归属规则：只在通用组件（形态 B）显示，绑定组件（形态 C）不重复 */}
          {!isStale && variant === 'B' && UNRECOGNIZED_COUNT > 0 && (
            <span
              onClick={(e) => { e.stopPropagation(); setUnrecOpen(true) }}
              aria-label={`${UNRECOGNIZED_COUNT} 条取件短信未识别，点此处理`}
              style={{
                fontSize: 10, fontWeight: 700, lineHeight: 1,
                color: 'var(--on-dark)', background: 'var(--adm-color-warning)',
                borderRadius: 8, padding: '3px 5px', cursor: 'pointer', flexShrink: 0,
              }}
            >
              ⚠ {UNRECOGNIZED_COUNT} 条未识别
            </span>
          )}
        </div>

        {/* 形态 A：未绑定 · 空。组件版的 empty-first-time，同样承担教学职责 */}
        {variant === 'A' && (
          <div
            onClick={() => navigate('/onsite/fengchao-3?demo=1')}
            style={{
              flex: 1, display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center', gap: 6,
              cursor: 'pointer', textAlign: 'center', padding: '0 4px',
            }}
          >
            <span style={{ fontSize: 13, color: 'var(--onsite-ink)', opacity: 0.6, lineHeight: 1.5 }}>
              在等第一条<br />取件短信
            </span>
            <span style={{ fontSize: 11, color: 'var(--adm-color-primary)', fontWeight: 600 }}>
              点我看看取件时长什么样
            </span>
          </div>
        )}

        {/* 形态 D：绑定点已清空。不能空白（用户以为坏了）也不能移除 */}
        {variant === 'D' && (
          <div style={{
            flex: 1, display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', gap: 4,
          }}>
            <CheckOutline fontSize={22} color="var(--adm-color-success)" />
            <span style={{ fontSize: 12, color: 'var(--onsite-ink)', opacity: 0.45 }}>无待取</span>
            <span style={{ fontSize: 10, color: 'var(--onsite-ink)', opacity: 0.38 }}>丰巢柜·3号楼</span>
          </div>
        )}

        {/* 形态 C：已绑定单点。免点击概览——多组件方案剩下的唯一独有价值 */}
        {variant === 'C' && bound && (
          <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <div
              onClick={() => navigate(`/onsite/${bound.point.id}`)}
              style={{
                display: 'flex', alignItems: 'baseline', gap: 4, cursor: 'pointer',
                borderLeft: `4px solid ${URGENCY_COLOR[bound.worst]}`, paddingLeft: 6,
              }}
            >
              <span style={{
                fontSize: 11, fontWeight: 600, color: 'var(--onsite-ink)',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {bound.point.shortName}
              </span>
              <span style={{ fontSize: 10, color: 'var(--onsite-ink)', opacity: 0.5, marginLeft: 'auto' }}>
                {bound.items.length} 件
              </span>
            </div>
            {bound.items.slice(0, 2).map((p) => (
              <div
                key={p.id}
                onClick={() => navigate(`/onsite/${bound.point.id}`)}
                style={{
                  background: 'var(--surface-inset)', borderRadius: 8, padding: '4px 6px',
                  cursor: 'pointer', display: 'flex', flexDirection: 'column', gap: 1,
                }}
              >
                <span style={{
                  fontFamily: 'var(--onsite-mono)', fontSize: 15, fontWeight: 700,
                  color: 'var(--onsite-ink)', fontVariantNumeric: 'tabular-nums', lineHeight: 1.1,
                }}>
                  {p.code}
                </span>
                {/* 无柜号时整行消失、码占满——不是留白，是重排 */}
                {p.lockerNo && (
                  <span style={{ fontSize: 9, color: 'var(--onsite-ink)', opacity: 0.5 }}>
                    {bound.point.kind === 'locker' ? '格口' : '货架'} {p.lockerNo}
                  </span>
                )}
              </div>
            ))}
            {bound.items.length > 2 && (
              <span style={{ fontSize: 9, color: 'var(--onsite-ink)', opacity: 0.45, textAlign: 'center' }}>
                +{bound.items.length - 2} 件 · 点开看全部
              </span>
            )}
          </div>
        )}

        {/* 形态 B：通用组件，按取件点分节 */}
        {variant === 'B' && shown.map((s) => (
          <div
            key={s.point.id}
            // tap 分节 = 声明「我在这个点」→ 1 步直进现场模式
            onClick={() => navigate(`/onsite/${s.point.id}`)}
            aria-label={`${s.point.shortName}，${s.items.length} 件待取，点此查看取件码`}
            style={{
              // minHeight 44 是硬约束：点名行绝不能被后续内容挤掉。
              // 见本文件下方「反向绑定提示不放这里」的回归记录。
              flex: 1, minHeight: 44, borderRadius: 10, padding: '6px 8px',
              background: 'var(--surface-inset)',
              borderLeft: `4px solid ${URGENCY_COLOR[s.worst]}`,
              cursor: 'pointer', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 2,
            }}
          >
            <div style={{
              fontSize: 12, fontWeight: 600, color: 'var(--onsite-ink)',
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>
              {s.point.shortName}
            </div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
              <span style={{
                fontSize: 20, fontWeight: 700, color: 'var(--onsite-ink)',
                fontVariantNumeric: 'tabular-nums', lineHeight: 1,
              }}>
                {s.items.length}
              </span>
              <span style={{ fontSize: 10, color: 'var(--onsite-ink)', opacity: 0.5 }}>件</span>
              {s.worst !== 'safe' && (
                <span style={{
                  fontSize: 10, fontWeight: 600, marginLeft: 'auto',
                  color: URGENCY_COLOR[s.worst], fontVariantNumeric: 'tabular-nums',
                }}>
                  {s.worst === 'due-soon' ? ticking : URGENCY_LABEL[s.worst]}
                </span>
              )}
            </div>
          </div>
        ))}

        {variant === 'B' && rest > 0 && (
          <div
            onClick={() => navigate('/home')}
            style={{
              fontSize: 10, textAlign: 'center', color: 'var(--onsite-ink)',
              opacity: 0.55, cursor: 'pointer', flexShrink: 0,
            }}
          >
            +{rest} 个点
          </div>
        )}

        {/* 反向绑定提示不放这里。
            试过放在组件底部，结果 176px 固定高度装不下、取件点名字被裁掉——
            而没有名字的取件点分节毫无意义。桌面组件是全产品空间最紧张的表面，
            它必须永远保住的那一行就是点名。提示改放首页横幅（见 Screen6）。
            → 同时说明 Story 3 AC4 的措辞要改：「组件上出现轻提示」是错的落点。 */}
      </div>

      {/* 形态切换器 + 陈旧开关（仅 mockup 评审用，真实产品没有） */}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'center' }}>
        {(['B', 'A', 'C', 'D'] as const).map((v) => (
          <span key={v} onClick={() => setVariant(v)} style={{
            padding: '6px 12px', borderRadius: 16, fontSize: 12, cursor: 'pointer',
            background: variant === v ? 'var(--on-dark)' : 'rgba(255,255,255,0.2)',
            color: variant === v ? '#1f2937' : 'var(--on-dark)', fontWeight: 600,
          }}>
            形态 {v}
          </span>
        ))}
        <span onClick={() => setStale(!stale)} style={{
          padding: '6px 12px', borderRadius: 16, fontSize: 12, cursor: 'pointer',
          background: stale ? 'var(--adm-color-warning)' : 'rgba(255,255,255,0.2)',
          color: 'var(--on-dark)', fontWeight: 600,
        }}>
          数据陈旧
        </span>
      </div>
      <div style={{ color: 'var(--on-dark)', opacity: 0.6, fontSize: 11, textAlign: 'center', lineHeight: 1.7, maxWidth: 300 }}>
        数据新鲜度：更新于 {stale ? 190 : WIDGET_AGE_MINUTES} 分钟前 ·
        阈值 {STALE_THRESHOLD_MINUTES} 分钟<br />
        陈旧的码比没有码更危险——用户会拿着三天前的码去柜机前
      </div>

      {/* 未识别收容 Popup（B-r2-1 修复）：角标不再是死点击 */}
      <Popup visible={unrecOpen} onMaskClick={() => setUnrecOpen(false)} bodyStyle={{ borderTopLeftRadius: 16, borderTopRightRadius: 16 }}>
        <div style={{ padding: 16 }}>
          <div style={{ fontSize: 17, fontWeight: 700, marginBottom: 4 }}>
            {UNRECOGNIZED_COUNT} 条取件短信没认出来
          </div>
          <div style={{ fontSize: 12, opacity: 0.6, marginBottom: 12, lineHeight: 1.6 }}>
            5G 消息、无发件人号码的网络短信、微信折叠通知——这几类识别不了是已知限制。
            我们宁可让你知道漏了，也不要让你站在柜机前才发现。
          </div>
          <List>
            {UNRECOGNIZED_SMS.map((u) => (
              <List.Item
                key={u.id}
                title={<span style={{ fontSize: 13 }}>{u.sender}</span>}
                description={
                  <span style={{ fontSize: 12, lineHeight: 1.6 }}>
                    {u.body}<br />
                    <span style={{ opacity: 0.55 }}>原因：{u.reason}</span>
                  </span>
                }
              >
                <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--adm-color-primary)' }}>
                  手动提取
                </span>
              </List.Item>
            ))}
          </List>
          <div style={{ fontSize: 11, opacity: 0.5, marginTop: 12, lineHeight: 1.6 }}>
            「报告给社区」会上传这条短信的文本内容以改进识别，<b>默认不勾选</b>。
            你的取件码永不出本机。
          </div>
          <Button block size="large" onClick={() => setUnrecOpen(false)} style={{ marginTop: 12, '--border-radius': '12px' } as CSSProperties}>
            知道了
          </Button>
          <SafeArea position="bottom" />
        </div>
      </Popup>
    </div>
  )
}

{/* → User taps 取件点分节 → SCREEN 2: On-site Main */}
{/* → User taps 组件标题 / "+N 个点" → SCREEN 6: Home */}

/* ================================================
   FLOW: On-site Present
   SCREEN 2 of 6: On-site Main（现场模式 · 主屏）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 1 tap 某个取件点分节（1 次点击，可度量成功标准）
   EXIT:   tap「已取」→ SCREEN 5 瞬态 → 自动滑到下一件
   EXIT:   全部取完 → SCREEN 6: Home（该点件数归零，绿色成功态）
   BRANCH: swipe ←→ 换件（同组内，**不跨代取组**）
   BRANCH: swipe ↓ / 左上返回 → 退出，已标记的「已取」保留（记录的是物理事实）
   BRANCH: tap「平铺」→ SCREEN 4（8 件时的必要例外）
   BRANCH: 件数 > 4 → 自动在顶部显示「平铺」入口
   -----------------------------------------------
   STATE: default — 有柜号，单码全屏
   STATE: no-locker — 柜号区整个消失、码放大 15%（见 SCREEN 3）
   STATE: stale-code — 「3 天前的码，可能已失效」，不阻断出示（见 SCREEN 3）
   STATE: family-group — 代取件单独分区，轮播不跨组（见 SCREEN 3）
   STATE: empty — 该点无件，非空白屏（见 SCREEN 3）
   STATE: all-picked — 该点取完，绿色成功态
   STATE: demo — 演示态，斜向水印 +「已取」不污染真实数据
   ================================================ */
export function Screen2_Onsite() {
  const navigate = useNavigate()
  const { pointId = 'fengchao-3' } = useParams()
  const [params] = useSearchParams()
  const isDemo = params.get('demo') === '1'
  const forceEmpty = params.get('empty') === '1'
  const focusParam = Number(params.get('focus') ?? 'NaN')

  const point = PICKUP_POINTS.find((p) => p.id === pointId) ?? PICKUP_POINTS[0]
  const useOverflow = params.get('overflow') === '1'
  const all = useMemo(
    () => (useOverflow ? PARCELS_OVERFLOW : PARCELS).filter((x) => x.pickupPointId === pointId),
    [pointId, useOverflow],
  )
  const [pickedIds, setPickedIds] = useState<string[]>([])
  const [group, setGroup] = useState<'self' | 'family'>('self')
  const [index, setIndex] = useState(Number.isFinite(focusParam) ? focusParam : 0)
  const [allDone, setAllDone] = useState(false)
  const [dragDx, setDragDx] = useState(0)
  const touchRef = useRef<number | null>(null)

  const live = all.filter((x) => !pickedIds.includes(x.id))
  const familyCount = live.filter((x) => x.owner === 'family').length
  const selfCount = live.filter((x) => x.owner === 'self').length
  // 代取件单独成组；轮播只在当前组内进行 → 天然不跨组
  const items = live.filter((x) => (group === 'family' ? x.owner === 'family' : x.owner === 'self'))
  const current = items[Math.min(index, Math.max(items.length - 1, 0))]
  const ticking = useTickingMinutes(252, current?.urgency === 'due-soon' && current.pickupPointId === 'fengchao-3')

  const markPicked = (id: string) => {
    // 立刻视觉反馈，不等任何异步——现场任何 spinner 都是失败
    setPickedIds((prev) => [...prev, id])
    Toast.show({
      duration: 3000,
      content: (
        <span style={{ fontSize: 15 }}>
          {isDemo ? '这是演示，不会记录' : '已标记取出'}
          {!isDemo && (
            <span
              onClick={() => setPickedIds((prev) => prev.filter((x) => x !== id))}
              style={{ marginLeft: 12, fontWeight: 700, color: '#fff', textDecoration: 'underline', cursor: 'pointer' }}
            >
              撤销
            </span>
          )}
        </span>
      ),
    })
    if (live.length <= 1) setAllDone(true)
  }

  // ── 该点无件：不能是空白屏，用户站在柜机前看到空白会恐慌
  if (forceEmpty || all.length === 0 || allDone) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--onsite-surface)', display: 'flex', flexDirection: 'column' }}>
        <OnsiteTopBar pointName={point.shortName} onBack={() => navigate('/home')} onAlert={() => {}} />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12, padding: 24 }}>
          {allDone
            ? <CheckOutline fontSize={64} color="var(--adm-color-success)" />
            : <span style={{ fontSize: 56 }}>📭</span>}
          <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--onsite-ink)' }}>
            {allDone ? '这个点已经取完了' : '这个点没有待取的件'}
          </div>
          <div style={{ fontSize: 14, color: 'var(--onsite-ink)', opacity: 0.5 }}>{LAST_PICKUP_LABEL}</div>
          <Button block size="large" onClick={() => navigate('/home')} style={{ marginTop: 16, '--border-radius': '12px', height: 52 } as CSSProperties}>
            返回首页
          </Button>
        </div>
        <SafeArea position="bottom" />
      </div>
    )
  }

  return (
    <div style={{
      minHeight: '100vh', background: 'var(--onsite-surface)',
      display: 'flex', flexDirection: 'column', position: 'relative',
    }}>
      {/* 演示态水印：与首页示例卡片视觉语言一致，避免被当成真数据 */}
      {isDemo && (
        <div style={{
          position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 5,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <span style={{
            fontSize: 60, fontWeight: 800, color: 'rgba(0,0,0,0.06)',
            transform: 'rotate(-24deg)', letterSpacing: '0.2em',
          }}>示例</span>
        </div>
      )}

      <OnsiteTopBar
        pointName={point.shortName}
        alert={!isDemo}
        onBack={() => navigate('/home')}
        onAlert={() => Toast.show({ duration: 4000, content: PERMISSION_ALERT })}
      />

      {/* 演示态退出条：常驻，退出后不残留任何数据 */}
      {isDemo && (
        <div style={{
          background: 'rgba(0,0,0,0.05)', padding: '8px 16px',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <span style={{ fontSize: 12, color: 'var(--onsite-ink)', opacity: 0.6 }}>这是演示数据</span>
          <span onClick={() => navigate('/')} style={{ fontSize: 12, fontWeight: 700, color: 'var(--adm-color-primary)', cursor: 'pointer' }}>
            退出演示
          </span>
        </div>
      )}

      {/* 代取件分区：轮播不跨组，切换需明确动作 */}
      {familyCount > 0 && selfCount > 0 && (
        <div style={{ display: 'flex', gap: 8, padding: '10px 16px 0' }}>
          {([['self', `我的 (${selfCount})`], ['family', `帮妈妈取 (${familyCount})`]] as const).map(([g, label]) => (
            <span key={g} onClick={() => { setGroup(g); setIndex(0) }} style={{
              flex: 1, textAlign: 'center', padding: '9px 0', borderRadius: 10, fontSize: 14,
              fontWeight: 700, cursor: 'pointer',
              background: group === g ? 'var(--onsite-ink)' : 'rgba(0,0,0,0.045)',
              color: group === g ? '#fff' : 'var(--onsite-ink)',
              opacity: group === g ? 1 : 0.6,
            }}>
              {g === 'family' && <TeamOutline fontSize={14} style={{ marginRight: 4, verticalAlign: '-2px' }} />}
              {label}
            </span>
          ))}
        </div>
      )}

      {/* 轮播：手写手势，只覆盖当前组 → 天然不跨代取组。
          不用 antd Swiper：其内部 slide 高度不受外部控制，
          取件码块无法垂直居中（本 mockup 的核心视觉决策会被破坏）。
          手势规约：swipe ←→ 换件，阈值 48px；swipe ↓ 退出由系统返回承担 */}
      <div
        style={{ flex: 1, minHeight: 0, overflow: 'hidden', touchAction: 'pan-y', display: 'flex', flexDirection: 'column' }}
        onTouchStart={(e) => { touchRef.current = e.touches[0].clientX }}
        onTouchMove={(e) => { if (touchRef.current !== null) setDragDx(e.touches[0].clientX - touchRef.current) }}
        onTouchEnd={() => {
          const dx = dragDx
          setDragDx(0)
          touchRef.current = null
          if (dx < -48 && index < items.length - 1) setIndex(index + 1)
          else if (dx > 48 && index > 0) setIndex(index - 1)
        }}
      >
        <div style={{
          flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column',
          transform: `translateX(${dragDx * 0.35}px)`,
          transition: dragDx === 0 ? 'transform 200ms ease' : 'none',
        }}>
          {/* 码可能已失效：提示但不阻断出示——阻断式设计在这里是错的 */}
          {current && (current.staleDays ?? 0) > 1 && (
            <NoticeBar
              content={`这是 ${current.staleDays} 天前的码，可能已失效 · 不影响出示`}
              color="alert"
              closeable
              extra={
                <span
                  onClick={() => Toast.show({ duration: 2000, content: '已刷新' })}
                  style={{ fontSize: 13, fontWeight: 700, cursor: 'pointer' }}
                >
                  刷新
                </span>
              }
              style={{ '--font-size': '13px', '--height': '38px' } as CSSProperties}
            />
          )}
          {current && (
            <OnsiteCodeBody
              parcel={current}
              lockerLabel={point.kind === 'locker' ? '格口' : '货架'}
              tickingLabel={current.urgency === 'due-soon' && current.deadlineLabel ? ticking : undefined}
            />
          )}
        </div>
      </div>

      {/* 位置指示 + 平铺入口。8 件时用户不想滑 8 次 */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16,
        padding: '4px 16px 8px',
      }}>
        <span style={{
          fontSize: 14, color: 'var(--onsite-ink)', opacity: 0.5,
          fontVariantNumeric: 'tabular-nums',
        }}>
          第 {Math.min(index, items.length - 1) + 1}/{items.length} 件
        </span>
        {items.length > 3 && (
          <span onClick={() => navigate(`/onsite/${pointId}/tiles${useOverflow ? '?overflow=1' : ''}`)} style={{
            fontSize: 13, fontWeight: 700, color: 'var(--adm-color-primary)', cursor: 'pointer',
            display: 'inline-flex', alignItems: 'center', gap: 2,
          }}>
            平铺看全部 <RightOutline fontSize={12} />
          </span>
        )}
      </div>

      {/* 「已取」：底部安全区之上、单手拇指可达、≥56pt（手套 + 水屏容错） */}
      <div style={{ padding: '8px 20px 4px', flexShrink: 0 }}>
        <Button
          block
          color="primary"
          disabled={!current}
          onClick={() => current && markPicked(current.id)}
          style={{
            '--border-radius': '16px',
            height: 64,
            fontSize: 22,
            fontWeight: 700,
            // 黑底白字：与整屏的纯黑 on 纯白对比策略一致，
            // 直射阳光下饱和蓝比黑更容易发灰
            '--background-color': 'var(--onsite-ink)',
            '--border-color': 'var(--onsite-ink)',
            '--text-color': '#fff',
          } as CSSProperties}
        >
          <CheckOutline fontSize={22} style={{ marginRight: 8, verticalAlign: '-3px' }} />
          已取
        </Button>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → swipe ←→ → 同组上一件/下一件（不跨代取组） */}
{/* → tap「已取」→ SCREEN 5: Picked Feedback（瞬态，3 秒内可撤销） */}
{/* → tap「全部平铺」→ SCREEN 4: Tile Overview */}
{/* → swipe ↓ / 左上返回 → SCREEN 6: Home（已标记的「已取」保留） */}

/* ================================================
   FLOW: On-site Present
   SCREEN 3 of 6: On-site Variants（现场模式 4 个变体）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  评审用画廊。真实产品里这些是 SCREEN 2 的条件分支，不是独立屏
   EXIT:   tap 任一变体 → SCREEN 2（带对应参数）
   -----------------------------------------------
   覆盖 edge.json 的 4 个必须目视确认的变体：
   ① boundary-null 无柜号 → 柜号区消失、码放大（不是留白，是重排）
   ② error-stale-code 码可能已失效 → NoticeBar 提示但**不阻断出示**
   ③ boundary-daqu 代取件分区 → 轮播不跨组
   ④ empty 该点无件 → 非空白屏
   ================================================ */
export function Screen3_Variants() {
  const navigate = useNavigate()
  const variants = [
    { id: 'nolocker', parcel: PARCELS.find((p) => p.id === 'p3')!, title: '① 无柜号 → 重排', desc: '柜号区整个消失，取件码放大 15%。不留空位、不显示「--」' },
    { id: 'stale', parcel: PARCELS.find((p) => p.id === 'p4')!, title: '② 码可能已失效', desc: '提示「3 天前的码」但不阻断出示——用户可能确实需要' },
    { id: 'daqu', parcel: PARCELS.find((p) => p.id === 'p6')!, title: '③ 代取件分区', desc: '「帮妈妈取」单独成组，轮播不跨组，切换需明确动作' },
    { id: 'empty', parcel: PARCELS.find((p) => p.id === 'p1')!, title: '④ 该点无件', desc: '不能是空白屏——站在柜机前看到空白会恐慌' },
  ]
  return (
    <div style={{ minHeight: '100vh', background: 'var(--onsite-surface)' }}>
      <NavBar backArrow={<LeftOutline />} onBack={() => navigate('/')} style={{ '--height': '48px' } as CSSProperties}>
        SCREEN 3 · 现场模式变体
      </NavBar>
      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {variants.map((v) => (
          <div key={v.id} style={{ border: '1px solid rgba(0,0,0,0.08)', borderRadius: 14, overflow: 'hidden' }}>
            <div style={{ padding: '10px 14px', background: 'rgba(0,0,0,0.03)' }}>
              <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--onsite-ink)' }}>{v.title}</div>
              <div style={{ fontSize: 12, color: 'var(--onsite-ink)', opacity: 0.55, marginTop: 2, lineHeight: 1.5 }}>{v.desc}</div>
            </div>
            {/* 缩略预览：真实渲染同一个 OnsiteCodeBody，缩放到 42% */}
            <div style={{ height: 190, overflow: 'hidden', position: 'relative', background: '#fff' }}>
              <div style={{
                position: 'absolute', top: 0, left: 0, width: '238%', height: '452px',
                transform: 'scale(0.42)', transformOrigin: 'top left',
                display: 'flex', flexDirection: 'column',
              }}>
                {v.id === 'empty' ? (
                  <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12 }}>
                    <span style={{ fontSize: 56 }}>📭</span>
                    <div style={{ fontSize: 22, fontWeight: 700 }}>这个点没有待取的件</div>
                    <div style={{ fontSize: 14, opacity: 0.5 }}>{LAST_PICKUP_LABEL}</div>
                  </div>
                ) : (
                  <>
                    {v.id === 'stale' && (
                      <NoticeBar content="这个取件码是 3 天前的，可能已失效" color="alert" closeable />
                    )}
                    {v.id === 'daqu' && (
                      <div style={{ display: 'flex', gap: 8, padding: '10px 16px 0' }}>
                        <span style={{ flex: 1, textAlign: 'center', padding: '9px 0', borderRadius: 10, fontSize: 14, background: 'rgba(0,0,0,0.045)', opacity: 0.6, fontWeight: 700 }}>我的 (1)</span>
                        <span style={{ flex: 1, textAlign: 'center', padding: '9px 0', borderRadius: 10, fontSize: 14, background: 'var(--onsite-ink)', color: '#fff', fontWeight: 700 }}>
                          <TeamOutline fontSize={14} style={{ marginRight: 4, verticalAlign: '-2px' }} />帮妈妈取 (1)
                        </span>
                      </div>
                    )}
                    <OnsiteCodeBody
                      parcel={v.parcel}
                      lockerLabel={v.parcel.pickupPointId === 'fengchao-3' ? '格口' : '货架'}
                    />
                  </>
                )}
              </div>
            </div>
            <div
              onClick={() => navigate(v.id === 'empty' ? '/onsite/jd-bianlidian?empty=1' : v.id === 'daqu' ? '/onsite/jd-bianlidian' : `/onsite/${v.parcel.pickupPointId}`)}
              style={{ padding: '10px 14px', fontSize: 13, fontWeight: 700, color: 'var(--adm-color-primary)', cursor: 'pointer', borderTop: '1px solid rgba(0,0,0,0.06)' }}
            >
              全屏打开这个变体 →
            </div>
          </div>
        ))}
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → tap「全屏打开」→ SCREEN 2（对应参数） */}

/* ================================================
   FLOW: On-site Present
   SCREEN 4 of 6: Tile Overview（平铺总览）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 2 tap「全部平铺」（件数 > 3 时才出现入口）
   EXIT:   tap 某件 → 回 SCREEN 2 并定位到该件
   BRANCH: 左上返回 → SCREEN 2（保持原位置）
   -----------------------------------------------
   双模哲学的必要例外：现场模式通常极低密度（单码占满屏），
   但 8 件时用户不想滑 8 次，需要中等密度总览。
   承认这个例外，不要为了哲学一致性牺牲可用性。
   ================================================ */
export function Screen4_Tiles() {
  const navigate = useNavigate()
  const { pointId = 'cainiao-dongmen' } = useParams()
  const [params] = useSearchParams()
  const useOverflow = params.get('overflow') === '1'
  const qs = useOverflow ? '?overflow=1' : ''
  const items = (useOverflow ? PARCELS_OVERFLOW : PARCELS)
    .filter((x) => x.pickupPointId === pointId)
  const point = PICKUP_POINTS.find((p) => p.id === pointId) ?? PICKUP_POINTS[1]

  return (
    <div style={{ minHeight: '100vh', background: 'var(--onsite-surface)', display: 'flex', flexDirection: 'column' }}>
      <NavBar backArrow={<LeftOutline />} onBack={() => navigate(`/onsite/${pointId}${qs}`)} style={{ '--height': '48px' } as CSSProperties}>
        {point.shortName} · 共 {items.length} 件
      </NavBar>
      <div style={{ flex: 1, padding: 12, overflowY: 'auto' }}>
        <Grid columns={2} gap={10}>
          {items.map((p, i) => (
            <Grid.Item key={p.id}>
              <div
                onClick={() => navigate(`/onsite/${pointId}?focus=${i}${useOverflow ? '&overflow=1' : ''}`)}
                style={{
                  borderRadius: 14, padding: '14px 12px', cursor: 'pointer',
                  border: `2px solid ${p.urgency === 'safe' ? 'rgba(0,0,0,0.08)' : URGENCY_COLOR[p.urgency]}`,
                  background: p.urgency === 'safe' ? 'rgba(0,0,0,0.02)' : '#fff',
                }}
              >
                <div style={{
                  fontFamily: 'var(--onsite-mono)', fontSize: 24, fontWeight: 700,
                  color: 'var(--onsite-ink)', fontVariantNumeric: 'tabular-nums',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }}>
                  {p.code}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 6 }}>
                  {p.lockerNo && (
                    <Tag style={{ '--font-size': '11px', fontFamily: 'var(--onsite-mono)' } as CSSProperties}>{p.lockerNo}</Tag>
                  )}
                  {p.urgency !== 'safe' && (
                    <span style={{ fontSize: 11, fontWeight: 700, color: URGENCY_COLOR[p.urgency] }}>
                      {URGENCY_LABEL[p.urgency]}
                    </span>
                  )}
                </div>
                <div style={{ fontSize: 11, color: 'var(--onsite-ink)', opacity: 0.45, marginTop: 4 }}>{p.carrier}</div>
              </div>
            </Grid.Item>
          ))}
        </Grid>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → tap 某件 → SCREEN 2 定位到该件 */}

/* ================================================
   FLOW: On-site Present
   SCREEN 5 of 6: Picked Feedback（已取瞬态，冻结供评审）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 2 tap「已取」
   EXIT:   3 秒后自动稳定 → 下一件；或 tap「撤销」→ 回 SCREEN 2 原件
   -----------------------------------------------
   真实产品里这不是独立屏，是 SCREEN 2 内的瞬态。此处冻结供评审目视确认。
   行业共识依据：Productivity 场景 Anti-Pattern「没有 Undo 机制」→
   「完成任务支持 Undo Complete，3 秒内可撤销，Toast『已完成 · 撤销』」
   ================================================ */
export function Screen5_Picked() {
  const navigate = useNavigate()
  const [countdown, setCountdown] = useState(3)
  useEffect(() => {
    const t = setInterval(() => setCountdown((c) => (c > 0 ? c - 1 : 0)), 1000)
    return () => clearInterval(t)
  }, [])

  return (
    <div style={{ minHeight: '100vh', background: 'var(--onsite-surface)', display: 'flex', flexDirection: 'column' }}>
      <OnsiteTopBar pointName="丰巢柜·3号楼" onBack={() => navigate('/onsite/fengchao-3')} onAlert={() => {}} />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, opacity: 0.35 }}>
        <div style={{ fontSize: 15, opacity: 0.6 }}>8-3-1024 已淡出 →</div>
        <div style={{ fontFamily: 'var(--onsite-mono)', fontSize: 64, fontWeight: 700, color: 'var(--onsite-ink)' }}>
          5-2-3391
        </div>
        <div style={{ fontSize: 13, opacity: 0.6 }}>自动滑到下一件</div>
      </div>
      {/* 撤销条：3 秒窗口。现场误触成本极高（手抖/手套/水屏/后面排队） */}
      <div style={{
        margin: '0 20px 20px', padding: '14px 18px', borderRadius: 14,
        background: 'var(--onsite-ink)', color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <span style={{ fontSize: 15, fontWeight: 600 }}>已标记取出</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 12, opacity: 0.55, fontVariantNumeric: 'tabular-nums' }}>{countdown}s</span>
          <span onClick={() => navigate('/onsite/fengchao-3')} style={{
            fontSize: 15, fontWeight: 800, textDecoration: 'underline', cursor: 'pointer',
            padding: '4px 8px',
          }}>撤销</span>
        </span>
      </div>
      <div style={{ padding: '0 20px 12px', fontSize: 11, color: 'var(--onsite-ink)', opacity: 0.4, lineHeight: 1.6 }}>
        决策 2：「已取」记录的是物理事实（包裹已在我手上），所以中途退出**保留**已标记项。
        3 秒窗口过后若发现误触，出口在首页/历史里「改回待取」——这是本次流程新补的设计。
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → 3 秒后 → SCREEN 2 下一件 */}
{/* → tap「撤销」→ SCREEN 2 原件恢复 */}

/* ================================================
   FLOW: On-site Present
   SCREEN 6 of 6: Home Updated（首页 · 该点已更新）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 2 全部取完 / swipe ↓ 退出 / tap 返回
   EXIT:   流程终点。tap 某取件点卡片 → 回 SCREEN 2
   BRANCH: 下拉刷新 → 触发一次主动同步（error-stale-data 的出口）
   -----------------------------------------------
   STATE: default — 取件点卡片列表，按紧急度排序
   STATE: empty-collection — 全取完了：**绿色成功态，不是灰色空态**
   STATE: permission-health-bar — 顶部常驻横条，不可被滚动隐藏
   ================================================ */
export function Screen6_Home() {
  const navigate = useNavigate()
  const [hideAlert, setHideAlert] = useState(false)
  const [hintDismissed, setHintDismissed] = useState(false)
  const counts = useMemo(() => {
    const m: Record<string, { total: number; worst: UrgencyLevel }> = {}
    for (const p of PARCELS) {
      const e = m[p.pickupPointId] ?? { total: 0, worst: 'safe' as UrgencyLevel }
      e.total += 1
      if (URGENCY_ORDER[p.urgency] < URGENCY_ORDER[e.worst]) e.worst = p.urgency
      m[p.pickupPointId] = e
    }
    return m
  }, [])
  // 三态驱动排序（锁定决策 / Story 2 AC3）：紧急度优先，件数只作次级排序。
  // 修复 Check finding M-r2-1——此前按件数排会把「3 件全 safe」排在「1 件过期风险」前面。
  const sorted = PICKUP_POINTS
    .filter((p) => (counts[p.id]?.total ?? 0) > 0)
    .sort((a, b) => {
      const ca = counts[a.id]
      const cb = counts[b.id]
      const byUrgency = URGENCY_ORDER[ca.worst] - URGENCY_ORDER[cb.worst]
      return byUrgency !== 0 ? byUrgency : cb.total - ca.total
    })

  return (
    <div style={{ minHeight: '100vh', background: 'var(--adm-color-background)', display: 'flex', flexDirection: 'column' }}>
      {/* permission-health-bar：跨屏常驻，不可被滚动隐藏 */}
      {!hideAlert && (
        <NoticeBar
          content={PERMISSION_ALERT}
          color="alert"
          icon={<ExclamationTriangleOutline />}
          closeable
          onClose={() => setHideAlert(true)}
          extra={
            <span
              onClick={() => Toast.show({ duration: 4500, content: PERMISSION_FIX_PATH })}
              style={{ fontSize: 13, fontWeight: 700, cursor: 'pointer' }}
            >
              去开启
            </span>
          }
          style={{ '--font-size': '13px' } as CSSProperties}
        />
      )}
      <NavBar back={null} style={{ '--height': '52px', background: 'var(--adm-color-background)' } as CSSProperties}>
        <span style={{ fontSize: 20, fontWeight: 800 }}>待取件</span>
      </NavBar>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        <PullToRefresh onRefresh={async () => { await new Promise((r) => setTimeout(r, 600)); Toast.show('已同步最新') }}>
          <div style={{ padding: '4px 16px 16px' }}>
            {/* 首页第一级是取件点卡片，不是按时间排列的包裹列表 */}
            <List style={{ '--border-top': 'none', '--border-bottom': 'none', borderRadius: 14, overflow: 'hidden' } as CSSProperties}>
              {sorted.map((p) => {
                const c = counts[p.id]
                if (!c) return null
                return (
                  <List.Item
                    key={p.id}
                    onClick={() => navigate(`/onsite/${p.id}`)}
                    prefix={
                      <span style={{
                        width: 10, height: 40, borderRadius: 5, display: 'block',
                        background: URGENCY_COLOR[c.worst],
                      }} />
                    }
                    title={
                      <span style={{ fontSize: 16, fontWeight: 700 }}>{p.shortName}</span>
                    }
                    description={
                      <span style={{ fontSize: 12, opacity: 0.6 }}>
                        {POINT_KIND_LABEL[p.kind]}
                        {p.closingLabel ? ` · ${p.closingLabel}` : ''}
                        {c.worst !== 'safe' ? ` · ${URGENCY_LABEL[c.worst]}` : ''}
                      </span>
                    }
                    extra={
                      <span style={{ textAlign: 'right' }}>
                        <span style={{ fontSize: 26, fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{c.total}</span>
                        <span style={{ fontSize: 12, opacity: 0.5, marginLeft: 2 }}>件</span>
                      </span>
                    }
                  />
                )
              })}
            </List>

            {/* 反向绑定提示（M-r2-6，方案丙关键一环）。
                落点在 App 内而非桌面组件——组件是全产品空间最紧张的表面，
                它必须永远保住「点名」那一行；此前放进组件导致名字被裁掉 */}
            {!hintDismissed && (
              <div style={{
                marginTop: 12, padding: '12px 14px', borderRadius: 14,
                background: 'var(--surface-inset)', display: 'flex', alignItems: 'center', gap: 10,
              }}>
                <span style={{ fontSize: 13, lineHeight: 1.5, flex: 1 }}>
                  想让某个取件点在桌面单独可见？<b>长按组件</b>可以只显示一个点。
                </span>
                <span
                  onClick={() => setHintDismissed(true)}
                  aria-label="关闭提示"
                  style={{ fontSize: 14, opacity: 0.4, cursor: 'pointer', padding: '4px 8px', flexShrink: 0 }}
                >
                  ✕
                </span>
              </div>
            )}

            {/* empty-collection：空列表在这里是成就，不是缺失 */}
            <div style={{ marginTop: 16, padding: 16, borderRadius: 14, background: 'rgba(0,180,42,0.07)', textAlign: 'center' }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--adm-color-success)' }}>
                取完一个点后，这里会变成「都取完了 🎉 本月共取 12 件」
              </div>
              <div style={{ fontSize: 12, opacity: 0.55, marginTop: 4 }}>
                绿色成功态，不是灰色空态——这是 mockup 说明，非真实 UI
              </div>
            </div>

            <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
              <Button block size="middle" onClick={() => navigate('/')} style={{ '--border-radius': '12px' } as CSSProperties}>
                <LoopOutline style={{ marginRight: 4 }} />回桌面组件
              </Button>
              <Button block size="middle" onClick={() => navigate('/variants')} style={{ '--border-radius': '12px' } as CSSProperties}>
                看现场模式变体
              </Button>
            </div>
          </div>
        </PullToRefresh>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

// ─────────────────────────────────────────────
// 评审用屏选择器（真实产品没有）
// ─────────────────────────────────────────────
export function ScreenPicker() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const targets: Array<[string, string]> = [
    ['/onboarding', 'F2-S1 引导 · 价值主张（无登录）'],
    ['/onboarding/demo', 'F2-S2 现场模式演示（示例水印）'],
    ['/onboarding/sms-permission', 'F2-S3 短信权限说明页'],
    ['/onboarding/notification-permission', 'F2-S4 通知监听说明页'],
    ['/onboarding/widget', 'F2-S5 桌面组件引导（可跳过）'],
    ['/onboarding/ready', 'F2-S6 已就绪但空 · 权限体检'],
    ['/', 'S1 桌面组件（形态 B/A/C/D + 陈旧开关）'],
    ['/onsite/fengchao-3', 'S2 现场模式 · 丰巢 1 件已超时（at-risk）'],
    ['/onsite/cainiao-dongmen', 'S2 现场模式 · 菜鸟 3 件（含无柜号 + 失效码）'],
    ['/onsite/jd-bianlidian', 'S2 现场模式 · 京东 2 件（代取件分区）'],
    ['/onsite/cainiao-dongmen?overflow=1', 'S2 现场模式 · 双十一 8 件（平铺入口出现）'],
    ['/onsite/fengchao-3?demo=1', 'S2 现场模式 · 演示态（Story 1 依赖）'],
    ['/onsite/jd-bianlidian?empty=1', 'S2 现场模式 · 该点无件（非空白屏）'],
    ['/onsite/cainiao-dongmen/tiles?overflow=1', 'S4 平铺总览 · 8 件网格'],
    ['/variants', 'S3 四个变体画廊（缩略预览）'],
    ['/picked', 'S5 已取瞬态 + 3 秒撤销'],
    ['/home', 'S6 首页 · 取件点卡片 + 权限横条'],
  ]
  return (
    <>
      <span onClick={() => setOpen(true)} style={{
        // 贴右边缘 + 垂直居中 + 半透明。
        // 不放底部：会压住各屏的主 CTA（现场模式的「已取」、引导屏的「先看看」）。
        // 不放顶部：会压住现场模式信息条上的权限告警图标。
        position: 'fixed', right: 0, top: '46%', zIndex: 999,
        width: 30, height: 52, borderRadius: '10px 0 0 10px',
        background: 'var(--adm-color-primary)',
        color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 11, fontWeight: 700, cursor: 'pointer', opacity: 0.5,
      }}>屏</span>
      <Popup visible={open} onMaskClick={() => setOpen(false)} bodyStyle={{ borderTopLeftRadius: 16, borderTopRightRadius: 16 }}>
        <div style={{ padding: '12px 0 8px' }}>
          <div style={{ padding: '0 16px 8px', fontSize: 13, opacity: 0.5 }}>跳转到任意屏（仅评审用）</div>
          <List>
            {targets.map(([path, label]) => (
              <List.Item key={path} onClick={() => { setOpen(false); navigate(path) }} arrow={false}>
                <span style={{ fontSize: 14 }}>{label}</span>
              </List.Item>
            ))}
          </List>
          <SafeArea position="bottom" />
        </div>
      </Popup>
    </>
  )
}
