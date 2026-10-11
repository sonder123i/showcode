// =============================================
// App Shell — 亮码 ShowCode（设计验证 mockup）
// Platform: H5 (Ant Design Mobile)
// ---------------------------------------------
// ⚠️ 无 TabBar，这是刻意的。
//
// H5 约束里的「TabBar 布局铁律」在本项目不适用：现场出示流程是
// 从桌面组件深链进入的**单任务 Stack**，用户目标是「清空这个取件点
// 然后离开」。任何底部 Tab 都会在这一屏抢注意力、并诱导用户离开任务。
//
// 管理模式（首页/历史/设置）将来是否需要 TabBar，是 Story 2 的 IA 问题，
// 不在本 flow 范围内。本 Shell 只提供路由 + 单层容器 + 评审用屏选择器。
//
// 右下角「屏」按钮是评审工具（真实产品没有），可直接跳到任意屏与变体，
// 包括平时要点好几下才能到达的边界态。
// =============================================

import { Routes, Route, Navigate } from 'react-router-dom'
import {
  Screen1_Widget, Screen2_Onsite, Screen3_Variants,
  Screen4_Tiles, Screen5_Picked, Screen6_Home, ScreenPicker,
} from '../flow-1/flow1-onsite-present'
import {
  Onboard1_Welcome, Onboard2_Demo, Onboard3_SmsPermission,
  Onboard4_NotificationPermission, Onboard5_Widget, Onboard6_Ready,
} from '../flow-2/flow2-onboarding'

export default function ShowCodeShell() {
  return (
    <div style={{ width: '100%', height: '100%', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, minHeight: 0 }}>
        <Routes>
          {/* Flow 2：引导与权限（用户实际的第一条路径） */}
          <Route path="/onboarding" element={<Onboard1_Welcome />} />
          <Route path="/onboarding/demo" element={<Onboard2_Demo />} />
          <Route path="/onboarding/sms-permission" element={<Onboard3_SmsPermission />} />
          <Route path="/onboarding/notification-permission" element={<Onboard4_NotificationPermission />} />
          <Route path="/onboarding/widget" element={<Onboard5_Widget />} />
          <Route path="/onboarding/ready" element={<Onboard6_Ready />} />

          {/* Flow 1：现场出示 */}
          <Route path="/" element={<Screen1_Widget />} />
          <Route path="/onsite/:pointId" element={<Screen2_Onsite />} />
          <Route path="/onsite/:pointId/tiles" element={<Screen4_Tiles />} />
          <Route path="/variants" element={<Screen3_Variants />} />
          <Route path="/picked" element={<Screen5_Picked />} />
          <Route path="/home" element={<Screen6_Home />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
      <ScreenPicker />
    </div>
  )
}
