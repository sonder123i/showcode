import { BrowserRouter } from 'react-router-dom'
import ShowCodeShell from './flows/showcode/showcode'

// 单层 maxWidth 容器（H5 约束：App.tsx 只负责这一层，Shell 内不再嵌套）
// 430px 是主流安卓机视口上限；在桌面浏览器里它会把 mockup 框成手机宽度，
// 便于目视确认字号与触控区比例。
export default function App() {
  return (
    <BrowserRouter>
      <div
        style={{
          width: '100%',
          maxWidth: 430,
          margin: '0 auto',
          minHeight: '100vh',
          background: '#fff',
          position: 'relative',
          boxShadow: '0 0 40px rgba(0,0,0,0.10)',
        }}
      >
        <ShowCodeShell />
      </div>
    </BrowserRouter>
  )
}
