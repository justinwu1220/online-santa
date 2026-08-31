import { Link } from 'react-router-dom'

export interface Crumb {
  label: string
  /** 沒有 to 的那一項是目前所在的頁面，不會是連結 */
  to?: string
}

/**
 * 麵包屑導航。
 *
 * 詳情頁是會被分享出去的——有人是從別人傳來的連結直接落在這裡，沒有瀏覽器上一頁
 * 可回。麵包屑讓他知道自己在哪一層，也給一條往上走的路。
 *
 * 最後一項是目前頁面，用 `aria-current="page"` 標記而不是做成連結。
 */
export function Breadcrumb({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="麵包屑">
      <ol className="flex flex-wrap items-center gap-1.5 text-sm">
        {items.map((item, index) => {
          const last = index === items.length - 1
          return (
            <li key={item.label} className="flex items-center gap-1.5">
              {index > 0 && <span aria-hidden className="crumb-sep text-slate-400">›</span>}
              {item.to && !last ? (
                <Link to={item.to} className="crumb-link text-slate-500 hover:underline">
                  {item.label}
                </Link>
              ) : (
                <span aria-current="page"
                  className="crumb-current max-w-[16rem] truncate font-medium text-slate-700">
                  {item.label}
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
