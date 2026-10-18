import { Link } from 'react-router-dom'

// 這個頁面一律 render 在 PublicLayout 底下（見 App.tsx 的萬用路由）。
export function NotFound() {
  return (
    <div className="empty" style={{ marginTop: 64 }}>
      <p aria-hidden style={{ fontSize: 44, margin: 0 }}>🎁</p>
      <h1 style={{ margin: '8px 0 0', fontSize: 24, fontWeight: 900, color: 'var(--fg)' }}>找不到這個頁面</h1>
      <Link to="/" className="btn">回到願望牆</Link>
    </div>
  )
}
