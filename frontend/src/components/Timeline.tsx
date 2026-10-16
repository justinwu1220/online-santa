import type { ClaimEventType, ClaimEventView } from '../lib/types'
import { CLAIM_EVENT_LABELS, formatDateTime } from '../lib/format'

/**
 * 認領歷程。事件由後端寫入，只增不改。
 *
 * @param pendingSteps 還沒走到的步驟，排在已發生事件之後，以灰色空心圓圈與灰色文字顯示。
 *                     只有認領者自己的頁面會傳——管理員看的是實際發生過的紀錄
 */
export function Timeline({ events, pendingSteps = [] }: {
  events: ClaimEventView[]
  pendingSteps?: ClaimEventType[]
}) {
  if (events.length === 0 && pendingSteps.length === 0) {
    return <p className="timeline-note text-sm text-slate-500">尚無紀錄</p>
  }

  return (
    <ol className="space-y-4">
      {events.map((event, index) => (
        <li key={`${event.eventType}-${event.occurredAt}`} className="flex gap-3">
          <div className="flex flex-col items-center">
            <span className="timeline-dot mt-1.5 h-2 w-2 shrink-0 rounded-full bg-santa-500" />
            {(index < events.length - 1 || pendingSteps.length > 0) && (
              <span className="timeline-line w-px flex-1 bg-santa-100" />
            )}
          </div>
          <div className="pb-1">
            <p className="timeline-title text-sm font-medium text-slate-700">
              {CLAIM_EVENT_LABELS[event.eventType]}
            </p>
            {event.note && (
              <p className="timeline-note text-sm text-slate-500">{event.note}</p>
            )}
            <p className="timeline-time mt-0.5 text-xs text-slate-400">
              {formatDateTime(event.occurredAt)}
            </p>
          </div>
        </li>
      ))}

      {pendingSteps.map((step, index) => (
        <li key={`pending-${step}`} className="flex gap-3">
          <div className="flex flex-col items-center">
            {/* 空心圓圈比已發生事件的實心小圓點大一些，一眼就能分出「還沒到」 */}
            <span className="timeline-dot-pending mt-1 h-3 w-3 shrink-0 rounded-full border
              border-slate-400" />
            {index < pendingSteps.length - 1 && (
              <span className="timeline-line-pending w-px flex-1 bg-slate-300" />
            )}
          </div>
          <div className="pb-1">
            <p className="timeline-title-pending text-sm font-medium text-slate-400">
              {CLAIM_EVENT_LABELS[step]}
            </p>
          </div>
        </li>
      ))}
    </ol>
  )
}
