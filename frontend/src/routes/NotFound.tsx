import { Link } from 'react-router-dom'

export function NotFound() {
  return (
    <section className="py-16 text-center">
      <p className="text-5xl">🎁</p>
      <h1 className="mt-4 text-2xl font-bold text-santa-700">找不到這個頁面</h1>
      <Link to="/" className="mt-6 inline-block text-berry-600 underline">
        回到願望牆
      </Link>
    </section>
  )
}
