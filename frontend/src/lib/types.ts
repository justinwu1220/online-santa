/**
 * 後端 API 的回應型別。
 *
 * 後端的 Jackson 設定為 non_null——值為 null 的欄位不會出現在 JSON 裡，
 * 因此這裡凡是可能為空的欄位一律標成選填。
 */

export type UserRole = 'DONOR' | 'ORG_MEMBER' | 'ADMIN'

export interface CurrentUser {
  userId: string
  email: string
  role: UserRole
  organizationId?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

// ---------------------------------------------------------------- 願望

export type WishStatus = 'DRAFT' | 'AVAILABLE' | 'CLAIMED' | 'FULFILLED' | 'ARCHIVED'

export interface WishPublicView {
  id: string
  title: string
  description?: string
  category: string
  categoryLabel: string
  ageRange: string
  ageRangeLabel: string
  priceRange: string
  priceRangeLabel: string
  childAlias: string
  interests?: string
  status: WishStatus
  publishedAt?: string
  organizationId: string
  organizationName: string
  imageUrl?: string
}

export interface WishOrgView {
  id: string
  title: string
  description?: string
  category: string
  ageRange: string
  priceRange: string
  childAlias: string
  interests?: string
  status: WishStatus
  editable: boolean
  deletable: boolean
  version: number
  publishedAt?: string
  createdAt: string
  updatedAt: string
  imageUrl?: string
}

export interface WishRequestBody {
  childAlias: string
  ageRange: string
  interests?: string
  title: string
  description?: string
  category: string
  priceRange: string
}

export interface FilterOption {
  value: string
  label: string
}

export interface WishFilterOptions {
  categories: FilterOption[]
  ageRanges: FilterOption[]
  priceRanges: FilterOption[]
}

// ---------------------------------------------------------------- 機構

export type OrganizationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'
export type ReleasePolicy = 'MANUAL' | 'AUTO'

export interface OrganizationView {
  id: string
  name: string
  contactEmail: string
  contactPhone?: string
  address?: string
  description?: string
  status: OrganizationStatus
  reviewNote?: string
  reviewedAt?: string
  releasePolicy: ReleasePolicy
  releaseAfterDays?: number
  canPublishWishes: boolean
  createdAt: string
}

export interface OrganizationReviewView extends Omit<OrganizationView, 'canPublishWishes' | 'releasePolicy' | 'releaseAfterDays'> {
  reviewedBy?: string
}

// ---------------------------------------------------------------- 認領

export type ClaimStatus =
  | 'CLAIMED' | 'SHIPPED' | 'RECEIVED' | 'COMPLETED' | 'RELEASED' | 'CANCELLED'

interface ClaimBase {
  id: string
  status: ClaimStatus
  wishId: string
  wishTitle: string
  childAlias: string
  claimedAt: string
  shipDeadlineAt?: string
  overdue: boolean
  shippedAt?: string
  receivedAt?: string
  completedAt?: string
  trackingCarrier?: string
  trackingNumber?: string
  donorMessage?: string
  releaseReason?: string
  unreadMessageCount: number
}

export interface ClaimDonorView extends ClaimBase {
  organizationName: string
}

export interface ClaimOrgView extends ClaimBase {
  donorName?: string
  donorEmail: string
  releasePolicySnapshot: ReleasePolicy
}

export type ClaimEventType =
  | 'CLAIMED' | 'SHIPPED' | 'RECEIVED' | 'COMPLETED'
  | 'RELEASED_MANUAL' | 'RELEASED_AUTO' | 'CANCELLED' | 'FEEDBACK_UPLOADED'

export interface ClaimEventView {
  eventType: ClaimEventType
  note?: string
  occurredAt: string
}

// ---------------------------------------------------------------- 附件與訊息

export type AttachmentPurpose = 'WISH_IMAGE' | 'SHIPPING_PROOF' | 'ORG_FEEDBACK'

export interface AttachmentView {
  id: string
  purpose: AttachmentPurpose
  url: string
  contentType: string
  sizeBytes?: number
  uploadedAt: string
}

export interface UploadUrlResponse {
  attachmentId: string
  uploadUrl: string
  contentType: string
  expiresAt: string
}

export interface MessageView {
  id: number
  body: string
  fromMe: boolean
  read: boolean
  sentAt: string
}

// ---------------------------------------------------------------- 管理後台

export interface ReleaseSweepResult {
  sweptAt: string
  overdueFound: number
  autoReleased: number
  wishesReturnedToWall: number
  flaggedForOrganization: number
}

// ---------------------------------------------------------------- 監控中心

/**
 * 全站統計。
 *
 * 狀態分佈用 map 而非固定欄位——後端補齊了所有可能的狀態（沒資料的補 0），
 * 因此前端可以直接逐項渲染，不必處理「這個狀態這次不見了」。
 */
export interface PlatformStats {
  organizations: Record<string, number>
  wishes: Record<string, number>
  claims: Record<string, number>
  users: Record<string, number>
  overdueClaims: number
  pendingOrganizations: number
  availableWishes: number
  generatedAt: string
}

export interface AdminWishView {
  id: string
  title: string
  childAlias: string
  ageRange: string
  category: string
  priceRange: string
  status: WishStatus
  organizationId: string
  organizationName: string
  publishedAt?: string
  createdAt: string
}

export interface AdminClaimView {
  id: string
  status: ClaimStatus
  wishId: string
  wishTitle: string
  childAlias: string
  organizationId: string
  organizationName: string
  donorName?: string
  donorEmail: string
  claimedAt: string
  shipDeadlineAt?: string
  overdue: boolean
  releasePolicySnapshot: ReleasePolicy
  shippedAt?: string
  receivedAt?: string
  completedAt?: string
  trackingCarrier?: string
  trackingNumber?: string
  releaseReason?: string
}

export type AdminAuditAction =
  | 'VIEW_CLAIM_DETAIL' | 'VIEW_CLAIM_ATTACHMENTS'
  | 'APPROVE_ORGANIZATION' | 'REJECT_ORGANIZATION' | 'RUN_RELEASE_SWEEP'

export interface AuditLogView {
  id: number
  adminEmail?: string
  action: AdminAuditAction
  targetType: 'CLAIM' | 'ORGANIZATION' | 'SYSTEM'
  targetId?: string
  detail?: string
  occurredAt: string
}
