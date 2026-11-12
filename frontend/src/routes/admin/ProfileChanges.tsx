import { LazyBox } from '../../components/console/LazyBox'
import { api, withQuery } from '../../lib/api'
import { ORGANIZATION_FIELD_LABELS, USER_FIELD_LABELS, fieldLabel } from '../../lib/fieldLabels'
import { formatDateTime } from '../../lib/format'
import type {
  FieldChange, PageResponse, ProfileChangeSource, ProfileChangeTargetType, ProfileChangeView,
} from '../../lib/types'

/** 異動是誰造成的。SYSTEM 要特別標出來——那是使用者沒按任何按鈕就被改掉的。 */
export const SOURCE_LABELS: Record<ProfileChangeSource, string> = {
  SELF_SERVICE: '本人修改',
  ADMIN: '管理員修改',
  SYSTEM: '系統自動',
  LEGACY: '舊紀錄轉入',
}

const TARGET_LABELS: Record<ProfileChangeTargetType, string> = {
  USER: '認領者',
  ORGANIZATION: '機構',
}

function labelsFor(targetType: ProfileChangeTargetType) {
  return targetType === 'USER' ? USER_FIELD_LABELS : ORGANIZATION_FIELD_LABELS
}

/**
 * 一筆紀錄裡的欄位變更（舊 → 新）。用 .wh-change 的樣式，與願望的「變更內容」一致：
 * 舊值加刪除線、新值粗體，一眼看得出方向。
 */
export function ChangeList({ changes, targetType }: {
  changes: FieldChange[]
  targetType: ProfileChangeTargetType
}) {
  if (changes.length === 0) {
    return <span className="auth-note">（這筆紀錄沒有可顯示的欄位）</span>
  }
  return (
    <div className="wh-changes">
      {changes.map((change) => (
        <div key={change.field} className="wh-change">
          <strong>{fieldLabel(change.field, labelsFor(targetType))}</strong>
          <span className="wh-change__old">{change.before || '（空）'}</span>
          <span className="wh-change__arrow" aria-hidden>→</span>
          <span className="wh-change__new">{change.after || '（空）'}</span>
        </div>
      ))}
    </div>
  )
}

/**
 * 純呈現的表格。抽屜裡的區塊用它（資料由 LazyBox 載入，避免重複打同一支 API ——
 * 那支每呼叫一次就寫一筆稽核）。
 *
 * @param showTarget 顯示「對象」欄；跨對象瀏覽時才需要
 */
export function ProfileChangeRows({ rows, showTarget }: {
  rows: ProfileChangeView[]
  showTarget?: boolean
}) {
  return (
    <div className="table-wrap">
      <table className="audit">
        <thead>
          <tr>
            <th scope="col">時間</th>
            {showTarget && <th scope="col">對象</th>}
            <th scope="col">變更內容</th>
            <th scope="col">操作者</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={showTarget ? 4 : 3} style={{ color: 'var(--fg-muted)' }}>
                還沒有任何個資異動紀錄。
              </td>
            </tr>
          ) : rows.map((row) => (
            <tr key={row.id}>
              <td>{formatDateTime(row.occurredAt)}</td>
              {showTarget && (
                <td>
                  {TARGET_LABELS[row.targetType]}
                  <br />
                  {row.targetLabel ?? <span style={{ color: 'var(--fg-muted)' }}>{row.targetId}</span>}
                </td>
              )}
              <td style={{ whiteSpace: 'normal' }}>
                <ChangeList changes={row.changes} targetType={row.targetType} />
              </td>
              <td>
                {row.actorEmail ?? '—'}
                <br />
                {/* 系統自動改動標紅：使用者沒按任何按鈕，資料就變了 */}
                <span style={row.source === 'SYSTEM'
                  ? { color: '#fca5a5', fontWeight: 700 }
                  : { color: 'var(--fg-muted)' }}>
                  {SOURCE_LABELS[row.source] ?? row.source}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * 抽屜裡的區塊：點擊載入（每次載入寫一筆稽核），只顯示最近 30 筆。
 * 要看更早的或跨對象比對，到「個資異動紀錄」頁。
 */
export function ProfileChangeBox({ targetType, targetId }: {
  targetType: ProfileChangeTargetType
  targetId: string
}) {
  return (
    <LazyBox key={`changes-${targetId}`} title="個資異動紀錄" secret button="載入異動紀錄"
      desc="這個對象自己修改資料的歷程（誰、什麼時候、從什麼改成什麼），最近 30 筆。"
      load={() => api.get<PageResponse<ProfileChangeView>>(
        withQuery('/api/admin/profile-changes', { targetType, targetId, page: 0, size: 30 }))}>
      {(data: PageResponse<ProfileChangeView>) => (
        <>
          <ProfileChangeRows rows={data.content} />
          {data.hasNext && (
            <p className="auth-note">還有更早的紀錄，請到「個資異動紀錄」頁查看。</p>
          )}
        </>
      )}
    </LazyBox>
  )
}
