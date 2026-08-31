import { Link } from 'react-router-dom'

// 這個頁面一律 render 在 PublicLayout 底下（見 App.tsx 的萬用路由），
// 是深色玻璃主題，因此直接寫死深色可讀的文字色，不需要 theme hook。
export function NotFound() {
  return (
    <section className="py-16 text-center">
      <p className="text-5xl">🎁</p>
      <h1 className="mt-4 text-2xl font-bold text-white">找不到這個頁面</h1>
      <Link to="/" className="mt-6 inline-block text-emerald-300 underline">
        回到願望牆
      </Link>
    </section>
  )
}
