import { useEffect, useState } from 'react'
import { pageTitle } from '../../lib/brand'
import type { ProfileChangeTargetType } from '../../lib/types'
import { ProfileChangeTable } from './ProfileChanges'

type Filter = '' | ProfileChangeTargetType

const FILTERS: { key: Filter; label: string }[] = [
  { key: '', label: '全部' },
  { key: 'USER', label: '認領者' },
  { key: 'ORGANIZATION', label: '機構' },
]

/**
 * 個資異動紀錄：誰在什麼時候把哪個欄位從什麼改成什麼。
 *
 * <p>目的是爭議舉證——上線後若有人主張「我沒有改過地址」「這支電話不是我填的」，這裡
 * 是唯一答得出來的地方。相對地，這一頁會顯示他人的新舊真實姓名與電話，所以
 * <strong>每次查閱都會寫一筆稽核紀錄</strong>，在「系統與稽核」頁看得到誰查過。
 */
export function AdminDataChanges() {
  const [filter, setFilter] = useState<Filter>('')

  useEffect(() => {
    document.title = pageTitle('個資異動紀錄｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  return (
    <section className="org-tab" aria-labelledby="adChangesTitle">
      <div className="org-head"><h1 id="adChangesTitle">個資異動紀錄</h1></div>

      <p className="auth-note">
        認領者與機構修改自己的資料時留下的紀錄，新的在前。紀錄只能新增、不能修改或刪除。
        <strong>你每查閱一次，都會在「系統與稽核」留下一筆紀錄。</strong>
      </p>

      <div className="org-filters" role="tablist" aria-label="依對象別篩選">
        {FILTERS.map((item) => (
          <button key={item.key || 'all'} type="button" role="tab" className="org-filter"
            aria-selected={item.key === filter} onClick={() => setFilter(item.key)}>
            {item.label}
          </button>
        ))}
      </div>

      <ProfileChangeTable key={filter} targetType={filter || undefined} showTarget />
    </section>
  )
}
