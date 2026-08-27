import type { ClaimStatus, OrganizationStatus, WishStatus } from '../lib/types'
import {
  CLAIM_STATUS_LABELS, ORGANIZATION_STATUS_LABELS, WISH_STATUS_LABELS,
} from '../lib/format'

type Tone = 'neutral' | 'positive' | 'progress' | 'warning' | 'muted'

const TONE_CLASSES: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-700',
  positive: 'bg-santa-100 text-santa-700',
  progress: 'bg-amber-100 text-amber-800',
  warning: 'bg-rose-100 text-berry-600',
  muted: 'bg-slate-100 text-slate-400',
}

function Badge({ tone, children }: { tone: Tone; children: React.ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}>
      {children}
    </span>
  )
}

const WISH_TONES: Record<WishStatus, Tone> = {
  DRAFT: 'neutral', AVAILABLE: 'positive', CLAIMED: 'progress',
  FULFILLED: 'positive', ARCHIVED: 'muted',
}

const CLAIM_TONES: Record<ClaimStatus, Tone> = {
  CLAIMED: 'progress', SHIPPED: 'progress', RECEIVED: 'progress',
  COMPLETED: 'positive', RELEASED: 'muted', CANCELLED: 'muted',
}

const ORGANIZATION_TONES: Record<OrganizationStatus, Tone> = {
  PENDING: 'progress', APPROVED: 'positive', REJECTED: 'warning', SUSPENDED: 'warning',
}

export const WishStatusBadge = ({ status }: { status: WishStatus }) =>
  <Badge tone={WISH_TONES[status]}>{WISH_STATUS_LABELS[status]}</Badge>

export const ClaimStatusBadge = ({ status }: { status: ClaimStatus }) =>
  <Badge tone={CLAIM_TONES[status]}>{CLAIM_STATUS_LABELS[status]}</Badge>

export const OrganizationStatusBadge = ({ status }: { status: OrganizationStatus }) =>
  <Badge tone={ORGANIZATION_TONES[status]}>{ORGANIZATION_STATUS_LABELS[status]}</Badge>

export const OverdueBadge = () => <Badge tone="warning">已逾期</Badge>
