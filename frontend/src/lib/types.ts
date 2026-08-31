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
  /** 資料庫裡的角色。實際生效的角色請用 effectiveRoleOf()——未驗證的信箱會被降級 */
  role: UserRole
  organizationId?: string
  emailVerified: boolean
}

/** 個人檔案設定頁使用的自身資料，見 GET/PATCH /api/me/profile */
export interface UserProfile {
  displayName: string
  phone?: string
  email: string
  emailVerified: boolean
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
  /** 承辦人姓名。是機構的屬性，不是某位使用者的 display_name */
  contactPerson: string
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
  /** 能否建立草稿。比 canPublishWishes 寬鬆——審核期間也能先準備內容 */
  canDraftWishes: boolean
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
  /**
   * 寄送目的地。只出現在捐贈者自己的認領上，願望牆與願望詳情沒有這兩個欄位——
   * 認領之後才需要知道寄去哪。機構註冊時必填，但舊資料可能為空
   */
  organizationAddress?: string
  organizationPhone?: string
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
  /** 有願望（以 createdAt 歸年）可選的年份，供「全站願望」頁的年度篩選下拉使用 */
  availableWishYears: number[]
  /** 有認領（以 claimedAt 歸年，cohort 口徑）可選的年份，供「全站認領」頁使用 */
  availableClaimYears: number[]
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

// ---------------------------------------------------------------- 年度回顧
//
// 年度一律為台北日曆年，認領以 claimedAt 定錨（cohort 制）：完成／釋回／取消都歸屬
// 認領當年，不看實際發生的時間。詳見後端 TaiwanYear 的說明。

export interface MonthlyCount {
  /** 1–12，已補零，固定 12 筆 */
  month: number
  count: number
}

export interface DailyCount {
  /** 1 到當月實際天數，已補零 */
  day: number
  count: number
}

export interface DonorAnnualSummary {
  year: number
  claimedCount: number
  completedCount: number
  /** 送禮的孩子數，只計已完成的認領、以 distinct 願望計 */
  childrenHelped: number
  /** 支持的機構數，只計已完成的認領、以 distinct 機構計 */
  organizationsSupported: number
  /** 有認領紀錄可選的年份，由新到舊排序 */
  availableYears: number[]
}

export interface OrganizationAnnualStats {
  year: number
  newWishes: number
  claimed: number
  completed: number
  completionRate: number
  /** 釋回總數，涵蓋機構手動收回與逾期自動釋回兩種 */
  released: number
  cancelled: number
  /** 前述釋回中，屬於逾期自動釋回的次數 */
  autoReleasedCount: number
  /** 平均完成天數（認領到完成），沒有任何完成筆數時不存在 */
  averageCompletionDays?: number
  /** 該年度認領、但完成時間落在隔年（或更晚）的筆數 */
  crossYearCompletions: number
  monthlyClaims: MonthlyCount[]
  availableYears: number[]
}

/** 機構「每月分布」長條圖點某個月之後的下鑽，見 GET /api/organizations/me/stats/monthly */
export interface OrganizationMonthlyStats {
  year: number
  month: number
  dailyClaims: DailyCount[]
}

export interface OrganizationCompletionRanking {
  organizationId: string
  organizationName: string
  completedCount: number
}

export interface PlatformAnnualStats {
  year: number
  newDonors: number
  newOrganizations: number
  activeDonors: number
  publishedWishes: number
  claimed: number
  completed: number
  completionRate: number
  monthlyClaims: MonthlyCount[]
  /** 只涵蓋三種終局狀態：COMPLETED／RELEASED／CANCELLED */
  claimOutcomes: Record<string, number>
  /** 該年度完成認領數前五名的機構，僅管理端可見 */
  topOrganizations: OrganizationCompletionRanking[]
  availableYears: number[]
}

/** 平台「每月趨勢」長條圖點某個月之後的下鑽，見 GET /api/admin/stats/monthly */
export interface PlatformMonthlyStats {
  year: number
  month: number
  dailyClaims: DailyCount[]
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
