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
  /** 真實姓名，用於捐贈收據與徵信名冊。既有帳號可能沒有 */
  name?: string
  phone?: string
  email: string
  emailVerified: boolean
  /** 已看完首次登入說明 */
  onboarded: boolean
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

export type WishStatus = 'DRAFT' | 'PENDING_REVIEW' | 'AVAILABLE' | 'CLAIMED' | 'FULFILLED'

/**
 * 願望牆清單的單筆項目。流量優化：清單不含 imageUrl，一律用分類圖示，圖片只在
 * 詳情頁（{@link WishPublicView}）載入。
 */
export interface WishListView {
  id: string
  title: string
  description?: string
  category: string
  categoryLabel: string
  ageRange: string
  ageRangeLabel: string
  priceRange?: string
  priceRangeLabel?: string
  childAlias: string
  interests?: string
  /** 頭貼檔名，建立願望時存下，位於 /avatars/ */
  avatar: string
  status: WishStatus
  publishedAt?: string
  organizationId: string
  organizationName: string
}

export interface WishPublicView {
  id: string
  title: string
  description?: string
  category: string
  categoryLabel: string
  ageRange: string
  ageRangeLabel: string
  priceRange?: string
  priceRangeLabel?: string
  childAlias: string
  interests?: string
  avatar: string
  status: WishStatus
  publishedAt?: string
  organizationId: string
  organizationName: string
  /** 機構簡介（機構設定），沒填為 undefined */
  organizationDescription?: string
  /** 機構官方網站，沒填為 undefined */
  organizationWebsite?: string
  /** 機構照片的限時網址，沒上傳為 undefined */
  organizationPhotoUrl?: string
  imageUrl?: string
  /** 孩子手寫的感謝卡／願望信照片，沒有就是空陣列（不是 undefined） */
  letterPhotoUrls: string[]
  /** 認領後預計要在幾天內寄出（依機構的逾期處理設定），認領前用來預告寄出日期 */
  shipDeadlineDays: number
}

export interface WishOrgView {
  id: string
  title: string
  description?: string
  category: string
  ageRange: string
  priceRange?: string
  childAlias: string
  interests?: string
  status: WishStatus
  /** 管理員退回時的原因（退回後到重新核准前一直保留） */
  reviewNote?: string
  editable: boolean
  deletable: boolean
  version: number
  publishedAt?: string
  createdAt: string
  updatedAt: string
  imageUrl?: string
  /**
   * 孩子手寫的感謝卡／願望信照片，沒有就是空陣列（不是 undefined）。帶附件完整
   * 資訊（而非只有網址）——機構後台要用 id 刪除自己上傳的照片
   */
  letterPhotos: AttachmentView[]
}

export interface WishRequestBody {
  childAlias: string
  ageRange: string
  interests?: string
  title: string
  description?: string
  category: string
  priceRange?: string
}

export interface FilterOption {
  value: string
  label: string
}

export interface WishFilterOptions {
  categories: FilterOption[]
  ageRanges: FilterOption[]
  organizations: FilterOption[]
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
  mobilePhone?: string
  address?: string
  preferredDeliveryTime?: string
  description?: string
  /** 官方網站（完整網址），沒填為 undefined */
  website?: string
  /** 機構照片的限時網址，沒上傳為 undefined */
  photoUrl?: string
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
  /** 機構填的實際收到日期（YYYY-MM-DD），和 receivedAt 這個確認時間分開；舊資料沒有 */
  receivedOn?: string
  completedAt?: string
  trackingCarrier?: string
  trackingNumber?: string
  /** 回報寄送時填寫的禮物資訊。舊資料沒有 */
  giftDescription?: string
  giftQuantity?: number
  giftPrice?: number
  /** 實際寄出日，YYYY-MM-DD */
  shippedOn?: string
  donorMessage?: string
  releaseReason?: string
  unreadMessageCount: number
}

export interface ClaimDonorView extends ClaimBase {
  ageRange: string
  ageRangeLabel: string
  avatar: string
  organizationName: string
  /** 機構簡介（機構設定），沒填為 undefined */
  organizationDescription?: string
  organizationWebsite?: string
  /** 只有單筆認領詳情會帶；清單沒有 */
  organizationPhotoUrl?: string
  /**
   * 寄送目的地。只出現在捐贈者自己的認領上，願望牆與願望詳情沒有這兩個欄位——
   * 認領之後才需要知道寄去哪。機構註冊時必填，但舊資料可能為空
   */
  organizationAddress?: string
  organizationPhone?: string
  organizationMobilePhone?: string
  organizationPreferredDeliveryTime?: string
  /** 是否申請了捐贈收據。只有旗標，收據內容只有管理員看得到 */
  receiptRequested: boolean
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

export type AttachmentPurpose =
  | 'WISH_IMAGE' | 'SHIPPING_PROOF' | 'ORG_FEEDBACK' | 'ORG_DOCUMENT' | 'WISH_LETTER'
  | 'RECEIPT_PROOF'

export interface AttachmentView {
  id: string
  purpose: AttachmentPurpose
  url: string
  contentType: string
  sizeBytes?: number
  uploadedAt: string
  /** 使用者上傳時的原始檔名；這支功能上線前的舊附件沒有，不存在時要有退回文字 */
  fileName?: string
}

export interface UploadUrlResponse {
  attachmentId: string
  uploadUrl: string
  contentType: string
  expiresAt: string
}

/** USER＝一般對話；WELCOME＝認領當下機構的招呼；SYSTEM＝系統通知（只有機構的願望對話裡有） */
export type MessageKind = 'USER' | 'SYSTEM' | 'WELCOME'

export interface MessageView {
  id: number
  body: string
  fromMe: boolean
  read: boolean
  sentAt: string
  kind: MessageKind
}

/** 機構看一個願望的對話：系統通知和目前認領者的訊息交錯在同一條時間線上。 */
export interface WishConversation {
  /** 目前認領者的顯示名稱；還沒有人認領為 undefined */
  claimantName?: string
  canSend: boolean
  unread: number
  messages: MessageView[]
}

/** 與平台團隊的一則訊息 */
export interface TeamMessageView {
  id: number
  body: string
  fromTeam: boolean
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

export interface AttachmentCleanupResult {
  cutoff: string
  found: number
  deleted: number
  failed: number
}

export interface DeadlineReminderResult {
  sweptAt: string
  found: number
  sent: number
  failed: number
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
  priceRange?: string
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
  giftDescription?: string
  giftQuantity?: number
  giftPrice?: number
  shippedOn?: string
  /** 是否申請了捐贈收據；內容要用專用端點讀取（會寫稽核） */
  receiptRequested: boolean
  releaseReason?: string
}

/** 管理員讀到的捐贈收據資料。身分證字號／統一編號是解密後的明文，機構與捐贈者端沒有這個型別。 */
export interface ClaimReceiptView {
  name: string
  idNumber: string
  email: string
  address: string
  proofs: AttachmentView[]
}

export type AdminMessageSenderRole = 'DONOR' | 'ORGANIZATION'

/**
 * 管理員檢視認領對話，比照 {@link MessageView} 不帶發言者身分——只標角色，
 * 沒有使用者 id、email。
 */
export interface AdminMessageView {
  id: number
  senderRole: AdminMessageSenderRole
  body: string
  sentAt: string
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
  | 'VIEW_CLAIM_DETAIL' | 'VIEW_CLAIM_ATTACHMENTS' | 'VIEW_CLAIM_MESSAGES'
  | 'VIEW_CLAIM_RECEIPT'
  | 'APPROVE_ORGANIZATION' | 'REJECT_ORGANIZATION' | 'VIEW_ORGANIZATION_DOCUMENTS'
  | 'SUSPEND_ORGANIZATION' | 'REACTIVATE_ORGANIZATION'
  | 'DELETE_ATTACHMENT' | 'DELETE_ORGANIZATION_DOCUMENT'
  | 'RUN_RELEASE_SWEEP' | 'RUN_ATTACHMENT_CLEANUP' | 'RUN_DEADLINE_REMINDERS'

export interface AuditLogView {
  id: number
  adminEmail?: string
  action: AdminAuditAction
  targetType: 'CLAIM' | 'ORGANIZATION' | 'SYSTEM'
  targetId?: string
  detail?: string
  occurredAt: string
}
