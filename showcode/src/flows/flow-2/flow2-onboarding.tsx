// =============================================
// FLOW: Onboarding & Permission Setup（新用户引导 + 权限授权，6 screens）
// Scenario: Health & Fitness · Flow 1（移动端权限密度最高的 onboarding 范式）
// Platform: H5 (Ant Design Mobile)
// ---------------------------------------------
// ⚠️ 设计验证 mockup，不是实现。真实 App 是 Kotlin + Compose。
//
// 沿用该范式的核心决策：**先理解价值，再请求权限**（行业共识：GO Club flow 6349、
// Miles flow 6704 均在权限请求前展示价值说明页，显著提升授权率）。
// 这正是 frame.json 里「权限按价值顺序请求，不按技术顺序」那条决策的落地。
//
// 与参照范式的 5 处结构性差异（不照搬的理由）：
//   1. 无登录——明确不做跨设备同步，所以没有账号体系，第一屏直接是价值主张
//   2. 「先理解价值」不用问卷，用**现场模式演示**——价值在一屏超大取件码里，不在问答里
//   3. 权限只有 2 类，但**短信权限的心理抗拒远高于健康数据**，说明页要更厚
//   4. 多一个范式里没有的环节：**加桌面组件引导**（组件是产品核心入口，不教等于白做）
//   5. onboarding 结束时**什么都没有**（还没收到快递）→ 必须靠演示态 + 空态兜住，
//      而健康类 onboarding 一结束就有生成的计划。这是最本质的差异。
// =============================================

import { useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import {
  NavBar, Button, ProgressBar, List, Toast, SafeArea, Tag,
} from 'antd-mobile'
import {
  LeftOutline, CheckOutline, CheckShieldOutline, ExclamationTriangleOutline,
  RightOutline, BellOutline, ReceiptOutline, SetOutline, EnvironmentOutline,
} from 'antd-mobile-icons'
import { useNavigate } from 'react-router-dom'
import type { Parcel } from '../shared/types'
import { OnsiteCodeBody } from '../flow-1/flow1-onsite-present'

// ── 演示用示例数据（与真实 mock 分离，明确标注为示例） ──
const DEMO_PARCELS: Parcel[] = [
  {
    id: 'd1', code: '8-3-1024', lockerNo: 'A12', carrier: '顺丰速运',
    pickupPointId: 'fengchao-3', owner: 'self', urgency: 'due-soon',
    deadlineLabel: '剩 4小时12分',
  },
  {
    id: 'd2', code: '5-2-3408', carrier: '圆通速递',
    pickupPointId: 'cainiao-dongmen', owner: 'self', urgency: 'safe',
    // 故意无柜号：演示「不是留白，是重排」
  },
]

/** 引导进度条。步骤 3-5 才计入「第 N / 4 步」，演示屏不占步数 */
function StepBar({ step }: { step: number }) {
  return (
    <div style={{ padding: '12px 16px 4px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
        <span style={{ fontSize: 12, opacity: 0.55 }}>第 {step} / 4 步</span>
        <span
          onClick={() => Toast.show({ duration: 2500, content: '随时可以在设置里改，跳过不会锁死任何功能' })}
          style={{ fontSize: 12, color: 'var(--adm-color-primary)', cursor: 'pointer' }}
        >
          跳过这一步
        </span>
      </div>
      <ProgressBar percent={(step / 4) * 100} />
    </div>
  )
}

/** 隐私承诺块。放在每一次权限请求的**上方**——先安心，再要权限 */
function PrivacyPromise() {
  return (
    <div style={{
      display: 'flex', gap: 10, alignItems: 'flex-start',
      padding: '12px 14px', borderRadius: 12, background: 'var(--surface-inset)',
    }}>
      <CheckShieldOutline fontSize={20} color="var(--adm-color-success)" style={{ flexShrink: 0, marginTop: 1 }} />
      <div style={{ fontSize: 12.5, lineHeight: 1.65, opacity: 0.8 }}>
        <b>你的取件码永不出本机。</b>
        解析全在手机本地完成，不上传、不注册、无广告。
        只有公共数据（驿站营业时间、快递柜收费规则）会下载。
      </div>
    </div>
  )
}

/**
 * 可多行的告警块。
 * 不用 antd NoticeBar：它是单行跑马灯，且 5.43 版没有 wrapable prop。
 * 实测会把「你会漏掉微信和各家 App 的推送通知」这半句裁掉、只剩「还有 2 项」——
 * 而本项目明确要求「不说权限不足，要列出漏掉了哪些来源」，被裁掉的正是来源。
 */
function AlertBlock({ tone = 'alert', action, onAction, children }: {
  tone?: 'alert' | 'info'; action?: string; onAction?: () => void; children: ReactNode
}) {
  const isAlert = tone === 'alert'
  return (
    <div style={{
      display: 'flex', gap: 10, alignItems: 'flex-start',
      padding: '12px 14px', borderRadius: 12,
      background: isAlert ? 'rgba(255,158,0,0.10)' : 'var(--surface-inset)',
      border: `1px solid ${isAlert ? 'var(--adm-color-warning)' : 'transparent'}`,
    }}>
      {isAlert
        ? <ExclamationTriangleOutline fontSize={18} color="var(--adm-color-warning)" style={{ flexShrink: 0, marginTop: 1 }} />
        : <CheckShieldOutline fontSize={18} style={{ flexShrink: 0, marginTop: 1, opacity: 0.6 }} />}
      <div style={{ fontSize: 13, lineHeight: 1.65, flex: 1 }}>{children}</div>
      {action && (
        <span onClick={onAction} style={{
          fontSize: 13, fontWeight: 700, flexShrink: 0, marginTop: 1,
          color: 'var(--adm-color-primary)', cursor: 'pointer',
        }}>
          {action}
        </span>
      )}
    </div>
  )
}

/* ================================================
   FLOW: Onboarding & Permission Setup
   SCREEN 1 of 6: Welcome / 价值主张
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  首次启动 App
   EXIT:   「先看看取件时什么样」→ SCREEN 2（演示）
   BRANCH: 「直接开始设置」→ SCREEN 3（跳过演示）
   -----------------------------------------------
   STATE: default — 无登录、无问卷，第一屏就是价值主张
   ================================================ */
export function Onboard1_Welcome() {
  const navigate = useNavigate()
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--adm-color-background)' }}>
      <div style={{ flex: 1, padding: '48px 24px 24px', display: 'flex', flexDirection: 'column' }}>
        <div style={{ fontSize: 40, marginBottom: 8 }}>📦</div>
        <div style={{ fontSize: 28, fontWeight: 800, lineHeight: 1.25 }}>亮码</div>
        <div style={{ fontSize: 17, opacity: 0.7, marginTop: 10, lineHeight: 1.6 }}>
          取快递时，不用再翻三个 App<br />找那些取件码。
        </div>

        <div style={{ margin: '28px 0', display: 'flex', flexDirection: 'column', gap: 12 }}>
          {[
            ['📩', '短信和通知里的取件码，自动收进来'],
            ['📍', '按取件点分好，到哪个点看哪个'],
            ['⏰', '快递柜还剩多少免费时间，一眼就知道'],
          ].map(([icon, text]) => (
            <div key={text} style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <span style={{ fontSize: 20, width: 26, textAlign: 'center', flexShrink: 0 }}>{icon}</span>
              <span style={{ fontSize: 15, lineHeight: 1.5 }}>{text}</span>
            </div>
          ))}
        </div>

        <PrivacyPromise />
      </div>

      <div style={{ padding: '0 24px 8px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {/* 主 CTA 是先看演示，不是先要权限——「先理解价值再请求权限」的落地 */}
        <Button block size="large" color="primary" onClick={() => navigate('/onboarding/demo')}
          style={{ '--border-radius': '14px', height: 54, fontSize: 17, fontWeight: 700 } as CSSProperties}>
          先看看取件时什么样
        </Button>
        <Button block fill="none" onClick={() => navigate('/onboarding/sms-permission')}
          style={{ height: 44 } as CSSProperties}>
          直接开始设置 <RightOutline fontSize={13} />
        </Button>
        <div style={{ textAlign: 'center', fontSize: 11.5, opacity: 0.45, paddingBottom: 8 }}>
          无需注册 · 无广告 · 开源
        </div>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → 「先看看取件时什么样」→ SCREEN 2: Demo */}
{/* → 「直接开始设置」→ SCREEN 3: SMS Permission */}

/* ================================================
   FLOW: Onboarding & Permission Setup
   SCREEN 2 of 6: On-site Demo（现场模式演示）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 1 主 CTA
   EXIT:   「退出演示，开始设置」→ SCREEN 3
   BRANCH: 「直接跳过」→ SCREEN 3
   -----------------------------------------------
   这是我们的「问卷」等价物。复用 flow1 导出的 OnsiteCodeBody，
   保证演示与真实现场模式是同一份渲染逻辑（字号不变量、无柜号重排都在里面）。
   STATE: demo — 斜向水印、「已取」不记录、退出后不残留
   ================================================ */
export function Onboard2_Demo() {
  const navigate = useNavigate()
  const [i, setI] = useState(0)
  const p = DEMO_PARCELS[i]

  return (
    <div style={{
      minHeight: '100vh', background: 'var(--onsite-surface)',
      display: 'flex', flexDirection: 'column', position: 'relative',
    }}>
      {/* 水印与首页示例卡片同一视觉语言——防止演示数据被当成真数据 */}
      <div style={{
        position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 5,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <span style={{ fontSize: 60, fontWeight: 800, color: 'rgba(0,0,0,0.06)', transform: 'rotate(-24deg)', letterSpacing: '0.2em' }}>
          示例
        </span>
      </div>

      <NavBar backArrow={<LeftOutline />} onBack={() => navigate('/onboarding/sms-permission')}
        style={{ '--height': '52px', borderBottom: '1px solid var(--hairline)' } as CSSProperties}>
        <span style={{ fontSize: 15, fontWeight: 600 }}>丰巢柜·3号楼</span>
      </NavBar>

      <div style={{ padding: '10px 16px 0', textAlign: 'center' }}>
        <span style={{
          fontSize: 12, fontWeight: 700, color: 'var(--adm-color-warning)',
          background: 'var(--surface-inset)', borderRadius: 8, padding: '4px 10px',
        }}>
          这是演示数据 · 你还没有收到任何快递
        </span>
      </div>

      <OnsiteCodeBody parcel={p} lockerLabel={p.lockerNo ? '格口' : undefined} />

      <div style={{ padding: '0 20px', display: 'flex', gap: 10, alignItems: 'center' }}>
        <span style={{ fontSize: 13, opacity: 0.5, fontVariantNumeric: 'tabular-nums' }}>
          第 {i + 1}/{DEMO_PARCELS.length} 件
        </span>
        <span onClick={() => setI(i === 0 ? 1 : 0)}
          style={{ fontSize: 13, fontWeight: 700, color: 'var(--adm-color-primary)', cursor: 'pointer', marginLeft: 'auto' }}>
          {i === 0 ? '看下一件（没有柜号的）' : '看上一件'}
        </span>
      </div>

      <div style={{ padding: '14px 20px 6px' }}>
        <Button block color="primary" size="large"
          onClick={() => { Toast.show({ duration: 2200, content: '这是演示，不会记录' }) }}
          style={{ '--border-radius': '14px', height: 58, fontSize: 20, fontWeight: 700,
            '--background-color': 'var(--onsite-ink)', '--border-color': 'var(--onsite-ink)', '--text-color': 'var(--on-dark)' } as CSSProperties}>
          <CheckOutline fontSize={20} style={{ marginRight: 6, verticalAlign: '-2px' }} />
          已取（点了不会记录）
        </Button>
      </div>

      <div style={{ padding: '6px 20px 10px' }}>
        <div style={{ fontSize: 12.5, lineHeight: 1.65, opacity: 0.6, marginBottom: 12, textAlign: 'center' }}>
          站在柜机前，你只需要看到这一个大字。<br />
          现在让手机自己把码收进来 ↓
        </div>
        <Button block size="large" onClick={() => navigate('/onboarding/sms-permission')}
          style={{ '--border-radius': '14px', height: 52, fontSize: 16, fontWeight: 700 } as CSSProperties}>
          退出演示，开始设置
        </Button>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → 「退出演示，开始设置」→ SCREEN 3: SMS Permission */}

/* ================================================
   FLOW: Onboarding & Permission Setup
   SCREEN 3 of 6: SMS Permission（短信权限说明页）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 2 退出演示 / SCREEN 1 直接开始设置
   EXIT:   「允许并继续」→ 触发系统弹窗 → SCREEN 4
   BRANCH: 「只用手动导入」→ SCREEN 4（不锁死任何功能）
   -----------------------------------------------
   STATE: default — 说明页在系统弹窗之前（先说明再触发）
   STATE: denied — 拒绝后仍可用，走手动导入路径
   ================================================ */
export function Onboard3_SmsPermission() {
  const navigate = useNavigate()
  const [denied, setDenied] = useState(false)

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--adm-color-background)' }}>
      <NavBar backArrow={<LeftOutline />} onBack={() => navigate('/onboarding')}
        style={{ '--height': '48px' } as CSSProperties} />
      <StepBar step={1} />

      <div style={{ flex: 1, padding: '16px 20px', overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
          <ReceiptOutline fontSize={28} color="var(--adm-color-primary)" />
          <span style={{ fontSize: 22, fontWeight: 800 }}>读取取件短信</span>
        </div>

        <div style={{ fontSize: 14.5, lineHeight: 1.7, opacity: 0.75, marginBottom: 20 }}>
          绝大多数驿站和快递柜会把取件码发到短信里。
          允许读取后，码会自动收进来——<b>这一步你什么都不用做</b>。
        </div>

        {/* 价值对比：不是「需要权限」，而是「有它 / 没它」的差别 */}
        <div style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
          {[
            { t: '允许', d: '全自动\n0 次操作', on: true },
            { t: '不允许', d: '每条自己粘贴\n约 3 秒/条', on: false },
          ].map((c) => (
            <div key={c.t} style={{
              flex: 1, padding: '14px 12px', borderRadius: 12,
              background: c.on ? 'var(--surface-inset)' : 'transparent',
              border: c.on ? '2px solid var(--adm-color-primary)' : '2px solid var(--hairline)',
            }}>
              <div style={{ fontSize: 12, fontWeight: 700, opacity: 0.6, marginBottom: 6 }}>{c.t}</div>
              <div style={{ fontSize: 14, fontWeight: 700, whiteSpace: 'pre-line', lineHeight: 1.5 }}>{c.d}</div>
            </div>
          ))}
        </div>

        <PrivacyPromise />

        {denied && (
          <div style={{ marginTop: 16 }}>
            <AlertBlock tone="info">
              不给短信权限也能用——每条取件短信你粘贴一次，约 3 秒。<b>所有功能都不会锁死。</b>
            </AlertBlock>
          </div>
        )}
      </div>

      <div style={{ padding: '8px 20px 10px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Button block color="primary" size="large"
          onClick={() => { Toast.show({ duration: 2000, content: '（mockup）此处触发系统短信权限弹窗' }) }}
          style={{ '--border-radius': '14px', height: 52, fontSize: 16, fontWeight: 700 } as CSSProperties}>
          允许并继续
        </Button>
        <Button block fill="none" onClick={() => { setDenied(true); navigate('/onboarding/notification-permission') }}
          style={{ height: 46 } as CSSProperties}>
          不用了，我自己粘贴
        </Button>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → 「允许并继续」→ 系统弹窗 → SCREEN 4 */}
{/* → 「不用了」→ SCREEN 4（记录为拒绝，功能不锁死） */}

/* ================================================
   FLOW: Onboarding & Permission Setup
   SCREEN 4 of 6: Notification Permission（通知监听说明页）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 3 任一选择之后
   EXIT:   「去开启」→ 跳系统设置（跳出 App）→ SCREEN 5
   BRANCH: 「就这样用」→ SCREEN 5
   -----------------------------------------------
   STATE: default — 明确列出「不开会漏掉哪些来源」，不说「权限不足」
   STATE: skipped — 只保留短信来源
   ================================================ */
export function Onboard4_NotificationPermission() {
  const navigate = useNavigate()
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--adm-color-background)' }}>
      <NavBar backArrow={<LeftOutline />} onBack={() => navigate('/onboarding/sms-permission')}
        style={{ '--height': '48px' } as CSSProperties} />
      <StepBar step={2} />

      <div style={{ flex: 1, padding: '16px 20px', overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
          <BellOutline fontSize={28} color="var(--adm-color-primary)" />
          <span style={{ fontSize: 22, fontWeight: 800 }}>读取通知</span>
        </div>

        <div style={{ fontSize: 14.5, lineHeight: 1.7, opacity: 0.75, marginBottom: 16 }}>
          有些取件码<b>根本不发短信</b>，只推送到 App 或微信。
          不开这个，你会漏掉下面这些来源：
        </div>

        {/* 具体来源清单——比「权限不足」有用得多 */}
        <List header="不开启会漏掉的来源" style={{ '--border-top': 'none', borderRadius: 12, overflow: 'hidden' } as CSSProperties}>
          {[
            ['微信「服务通知」', '很多驿站只推微信，且常被折叠成一条摘要'],
            ['菜鸟 App 推送', '淘系包裹常只在这里出现'],
            ['京东 App 推送', '自提点通知'],
            ['丰巢 App 推送', '入柜通知，部分柜机不发短信'],
          ].map(([t, d]) => (
            <List.Item key={t} description={<span style={{ fontSize: 12 }}>{d}</span>}>
              <span style={{ fontSize: 14 }}>{t}</span>
            </List.Item>
          ))}
        </List>

        <div style={{ fontSize: 12.5, lineHeight: 1.65, opacity: 0.6, margin: '16px 0' }}>
          这一步会跳到系统设置页，开启后手动返回本 App 即可。
          取件码仍然只在手机本地解析，不上传。
        </div>
        <PrivacyPromise />
      </div>

      <div style={{ padding: '8px 20px 10px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Button block color="primary" size="large"
          onClick={() => { Toast.show({ duration: 2200, content: '（mockup）此处跳出到系统「通知使用权」设置页' }) }}
          style={{ '--border-radius': '14px', height: 52, fontSize: 16, fontWeight: 700 } as CSSProperties}>
          去系统设置里开启
        </Button>
        <Button block fill="none" onClick={() => navigate('/onboarding/widget')} style={{ height: 46 } as CSSProperties}>
          就这样用，只收短信
        </Button>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → 「去系统设置里开启」→ 跳出 App → SCREEN 5 */}
{/* → 「就这样用」→ SCREEN 5 */}

/* ================================================
   FLOW: Onboarding & Permission Setup
   SCREEN 5 of 6: Widget Setup（桌面组件引导）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 4 任一选择之后
   EXIT:   「我已添加到桌面」→ SCREEN 6
   BRANCH: 「暂时跳过」→ SCREEN 6（不阻断）
   -----------------------------------------------
   范式里没有这一步，本产品必须有：组件是「≤1 次点击」这个可度量标准的载体。
   刻意做成可跳过——部分 ROM 添加小组件仍需额外权限，
   强制引导会把用户卡在第 5 步，比跳过更糟。
   STATE: default / done / skipped
   ================================================ */
export function Onboard5_Widget() {
  const navigate = useNavigate()
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--adm-color-background)' }}>
      <NavBar backArrow={<LeftOutline />} onBack={() => navigate('/onboarding/notification-permission')}
        style={{ '--height': '48px' } as CSSProperties} />
      <StepBar step={3} />

      <div style={{ flex: 1, padding: '16px 20px', overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
          <EnvironmentOutline fontSize={28} color="var(--adm-color-primary)" />
          <span style={{ fontSize: 22, fontWeight: 800 }}>放到桌面上</span>
        </div>

        <div style={{ fontSize: 14.5, lineHeight: 1.7, opacity: 0.75, marginBottom: 18 }}>
          这一步值得做：<b>瞟一眼桌面就知道该不该现在出门、先去哪个点</b>，
          不用解锁后翻 App。点某个取件点，直接跳到它的取件码。
        </div>

        {[
          ['1', '长按桌面空白处'],
          ['2', '点「小组件」/「小工具」'],
          ['3', '找到「亮码」，长按拖到桌面'],
        ].map(([n, t]) => (
          <div key={n} style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12 }}>
            <span style={{
              width: 26, height: 26, borderRadius: 13, flexShrink: 0,
              background: 'var(--adm-color-primary)', color: 'var(--on-dark)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 13, fontWeight: 800,
            }}>{n}</span>
            <span style={{ fontSize: 15 }}>{t}</span>
          </div>
        ))}

        <div style={{
          marginTop: 8, padding: '12px 14px', borderRadius: 12,
          background: 'var(--surface-inset)', display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <ExclamationTriangleOutline fontSize={18} color="var(--adm-color-warning)" style={{ flexShrink: 0, marginTop: 1 }} />
          <div style={{ fontSize: 12.5, lineHeight: 1.65, opacity: 0.8 }}>
            小米 / 华为等机型可能还要单独允许「创建桌面小组件」。
            <b>加不了也没关系，先跳过</b>，之后在设置里随时补。
          </div>
        </div>
      </div>

      <div style={{ padding: '8px 20px 10px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Button block color="primary" size="large" onClick={() => navigate('/onboarding/ready')}
          style={{ '--border-radius': '14px', height: 52, fontSize: 16, fontWeight: 700 } as CSSProperties}>
          我已添加到桌面
        </Button>
        <Button block fill="none" onClick={() => navigate('/onboarding/ready')} style={{ height: 46 } as CSSProperties}>
          暂时跳过
        </Button>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}

{/* → 「我已添加」/「暂时跳过」→ SCREEN 6: Ready */}

/* ================================================
   FLOW: Onboarding & Permission Setup
   SCREEN 6 of 6: Ready（已就绪，但还没有任何快递）
   PLATFORM: H5 (Ant Design Mobile)
   ------------------------------------------------
   ENTRY:  SCREEN 5 任一选择
   EXIT:   「进首页」→ 现场出示 flow 的 SCREEN 6（Home）
   BRANCH: 「手动导入一条试试」→ 手动导入（另一 flow）
   -----------------------------------------------
   与参照范式最本质的差异：健康类 onboarding 结束就有生成的计划，
   我们结束时**什么都没有**。所以这一屏的空态必须自己站得住：
   说清在等什么 + 权限体检 + 一条手动导入的退路。
   STATE: default — 全授权
   STATE: partial — 部分授权，横条列出漏了哪些来源
   STATE: miui — 小米设备专项：短信权限已给但「通知类短信」未开
   ================================================ */
export function Onboard6_Ready() {
  const navigate = useNavigate()
  const checks = [
    { label: '读取取件短信', ok: true, hint: '自动收录取件码' },
    { label: '读取通知', ok: false, hint: '微信 / 菜鸟 / 京东 / 丰巢 的推送收不到' },
    { label: '桌面小组件', ok: false, hint: '不装也能用，只是少了「瞟一眼」' },
  ]

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--adm-color-background)' }}>
      <NavBar back={null} style={{ '--height': '48px' } as CSSProperties} />
      <StepBar step={4} />

      <div style={{ flex: 1, padding: '16px 20px', overflowY: 'auto' }}>
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <CheckShieldOutline fontSize={44} color="var(--adm-color-success)" />
          <div style={{ fontSize: 22, fontWeight: 800, marginTop: 8 }}>设置好了</div>
          <div style={{ fontSize: 14, opacity: 0.6, marginTop: 6, lineHeight: 1.6 }}>
            现在在等第一条取件短信。<br />
            收到后它会自动出现在桌面组件和首页上。
          </div>
        </div>

        {/* 部分授权必须明确说漏了什么——不说「权限不足」 */}
        <div style={{ marginBottom: 12 }}>
          <AlertBlock action="去补上" onAction={() => navigate('/onboarding/notification-permission')}>
            还有 2 项没开启：你会漏掉<b>微信服务通知</b>和<b>菜鸟 / 京东 / 丰巢 App</b> 的推送。
          </AlertBlock>
        </div>

        <List header="权限体检" style={{ '--border-top': 'none', borderRadius: 12, overflow: 'hidden' } as CSSProperties}>
          {checks.map((c) => (
            <List.Item key={c.label} description={<span style={{ fontSize: 12 }}>{c.hint}</span>}
              extra={
                <Tag color={c.ok ? 'success' : 'default'}
                  style={{ '--font-size': '11px' } as CSSProperties}>
                  {c.ok ? '已开启' : '未开启'}
                </Tag>
              }>
              <span style={{ fontSize: 14 }}>{c.label}</span>
            </List.Item>
          ))}
        </List>

        {/* MIUI 专项：短信权限已给但「通知类短信」未开 → 网络短信静默收不到 */}
        <div style={{
          marginTop: 12, padding: '12px 14px', borderRadius: 12,
          background: 'var(--surface-inset)', display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <SetOutline fontSize={18} style={{ flexShrink: 0, marginTop: 1, opacity: 0.7 }} />
          <div style={{ fontSize: 12.5, lineHeight: 1.65, opacity: 0.8 }}>
            <b>小米 / 红米</b>还需单独开启「通知类短信」
            （权限管理 → 其他权限 → 通知类短信 → 始终允许），
            否则部分驿站的取件码收不到。
          </div>
        </div>
      </div>

      <div style={{ padding: '8px 20px 10px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Button block color="primary" size="large" onClick={() => navigate('/home')}
          style={{ '--border-radius': '14px', height: 52, fontSize: 16, fontWeight: 700 } as CSSProperties}>
          进首页
        </Button>
        <Button block fill="none"
          onClick={() => { Toast.show({ duration: 2500, content: '粘贴一条取件短信，自动提取取件码、驿站名和柜号' }) }}
          style={{ height: 46 } as CSSProperties}>
          等不及？手动导入一条试试
        </Button>
        <div style={{ textAlign: 'center', fontSize: 11.5, opacity: 0.45, paddingBottom: 4 }}>
          以上任何一项都能稍后在设置里改
        </div>
      </div>
      <SafeArea position="bottom" />
    </div>
  )
}
