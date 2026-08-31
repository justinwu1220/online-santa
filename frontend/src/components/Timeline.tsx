import type { ClaimEventView } from '../lib/types'
import { CLAIM_EVENT_LABELS, formatDateTime } from '../lib/format'

/** 認領歷程。事件由後端寫入，只增不改。 */
export function Timeline({ events }: { events: ClaimEventView[] }) {
  if (events.length === 0) {
    return <p className="timeline-note text-sm text-slate-500">尚無紀錄</p>
  }

  return (
    <ol className="space-y-4">
      {events.map((event, index) => (
        <li key={`${event.eventType}-${event.occurredAt}`} className="flex gap-3">
          <div className="flex flex-col items-center">
            <span className="timeline-dot mt-1.5 h-2 w-2 shrink-0 rounded-full bg-santa-500" />
            {index < events.length - 1 && (
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
    </ol>
  )
}
