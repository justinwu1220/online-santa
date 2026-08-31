import type { PageResponse } from '../lib/types'
import { Button } from './Form'

export function Pagination<T>({ page, onChange }: {
  page: PageResponse<T>; onChange: (next: number) => void
}) {
  if (page.totalPages <= 1) return null

  return (
    <nav className="mt-6 flex items-center justify-center gap-4" aria-label="分頁">
      <Button variant="secondary" disabled={page.page === 0}
        onClick={() => onChange(page.page - 1)}>
        上一頁
      </Button>
      <span className="surface-muted text-sm text-slate-500">
        第 {page.page + 1} / {page.totalPages} 頁　共 {page.totalElements} 筆
      </span>
      <Button variant="secondary" disabled={!page.hasNext}
        onClick={() => onChange(page.page + 1)}>
        下一頁
      </Button>
    </nav>
  )
}
