import type { PageResponse } from '../../lib/types'

/** 分頁。只有一頁時不顯示。 */
export function Pager<T>({ page, onChange }: {
  page: PageResponse<T>
  onChange: (next: number) => void
}) {
  if (page.totalPages <= 1) return null

  return (
    <nav className="pager" aria-label="分頁">
      <button type="button" className="btn btn--sm" disabled={page.page === 0}
        onClick={() => onChange(page.page - 1)}>
        上一頁
      </button>
      <span>第 {page.page + 1} / {page.totalPages} 頁　共 {page.totalElements} 筆</span>
      <button type="button" className="btn btn--sm" disabled={!page.hasNext}
        onClick={() => onChange(page.page + 1)}>
        下一頁
      </button>
    </nav>
  )
}
