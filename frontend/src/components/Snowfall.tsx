/**
 * 飄雪。純裝飾，不承載任何資訊。
 *
 * 雪花的位置與速度在模組載入時算一次，不在 render 裡跑 Math.random——那既是不純的
 * render，也會讓每次重新 render 的雪花原地跳動。裝飾不需要每次掛載都長得不一樣。
 *
 * `prefers-reduced-motion` 由 index.css 的 .snowflake 規則處理（直接不顯示）。
 */
const FLAKES = Array.from({ length: 24 }, () => ({
  left: `${Math.random() * 100}%`,
  size: `${Math.random() * 8 + 4}px`,
  duration: `${Math.random() * 10 + 12}s`,
  delay: `${Math.random() * 12}s`,
  opacity: Math.random() * 0.35 + 0.1,
}))

export function Snowfall() {
  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden="true">
      {FLAKES.map((flake, index) => (
        <span
          key={index}
          className="snowflake absolute top-0 rounded-full bg-white"
          style={{
            left: flake.left,
            width: flake.size,
            height: flake.size,
            opacity: flake.opacity,
            animation: `snowfall ${flake.duration} linear infinite`,
            animationDelay: flake.delay,
          }}
        />
      ))}
    </div>
  )
}
