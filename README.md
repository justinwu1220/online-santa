# 線上聖誕老人 (Online Santa)

讓兒童機構上架孩童的聖誕願望，一般大眾認領並送禮，形成
**願望發布 → 認領 → 寄送 → 追蹤回饋** 的完整閉環。

## 架構

```
使用者 / 機構
     │
     ▼
Cloud Run (Spring Boot API)   ── scale-to-zero，離峰 $0
     ├── Neon (PostgreSQL)     ── 願望、認領、追蹤狀態
     ├── Cloud Storage         ── 照片（前端 Signed URL 直傳）
     └── 帳號驗證（本機）       ── 密碼、信箱驗證碼、重設密碼，由 API 自己處理（驗證碼信走 SMTP）
```

前端 (React + Vite) 部署於 Firebase Hosting，`/api/**` 由 Hosting 轉給 Cloud Run（同網域，登入 cookie 才不會被當成第三方 cookie）。

## 技術棧

| 層 | 技術 |
|---|---|
| 後端 | Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security · Flyway |
| 資料庫 | PostgreSQL 16（本地 Docker / 正式 Neon） |
| 前端 | React 19 · TypeScript · Vite · TanStack Query · Tailwind CSS |
| 測試 | JUnit 5 · Testcontainers |
| CI/CD | GitHub Actions → Artifact Registry → Cloud Run |

## 本地開發

```bash
# 1. 啟動資料庫
docker compose up -d

# 2. 啟動後端（http://localhost:8080）
cd backend && ./mvnw spring-boot:run

# 3. 啟動前端（http://localhost:5173）
cd frontend && npm install && npm run dev
```

健康檢查：`curl http://localhost:8080/actuator/health`
API 文件：<http://localhost:8080/swagger-ui.html>

## 三個入口

三個在使用者感受上獨立的網站，共用同一套程式碼與身分系統：

| | 路徑 | 進入方式 |
|---|---|---|
| **主網站** | `/` | 願望牆公開瀏覽，右上角登入後可認領與查看紀錄 |
| **機構後台** | `/org` | 在 `/login` 註冊時選機構身分，或機構帳號登入後從右上角「機構後台」進入，導覽列**不含願望牆** |
| **監控中心** | `/admin` | 任何地方都沒有連結，直接輸入網址 |

> 監控中心不列出入口只是減少誤闖，**網址不是安全邊界** —— 真正的保護是後端的
> `hasRole('ADMIN')` 與本地 `users` 表的角色。

一個帳號只能有一種角色（資料庫的 `ck_users_org_membership` 強制這點）。機構登入頁
遇到一般民眾的帳號時會顯示申請表單，並警告成為機構成員後將無法再以個人身分認領。

### 管理員的稽核軌跡

管理員為了處理申訴需要跨機構檢視，看得到捐贈者個資與含孩童影像的收件證明。
「能看」與「看了不留痕跡」是兩件事——每次存取單筆認領詳情或附件都寫入
`admin_audit_logs`，且這份紀錄在 `/admin/system` 對所有管理員公開。

清單頁不寫稽核：它只顯示彙總與流程狀態，每次翻頁都記會把真正重要的紀錄淹沒。

## 前端

React 19 + TypeScript + Vite + Tailwind v4，涵蓋願望牆、認領流程、機構後台與審核後台。

身分驗證有兩種模式，由 `VITE_DEV_AUTH` 決定，與後端的 `dev-auth` 對稱：

| 模式 | 啟用條件 | 請求標頭 |
|---|---|---|
| 帳號（預設） | 沒設 `VITE_DEV_AUTH`，或不是 `true` | `Authorization: Bearer <access token>` |
| 開發 | `VITE_DEV_AUTH=true` | `X-Dev-User-Email` |

登入方式為 email/密碼。三種角色（認領者、機構、管理員）統一從 `/login` 登入，註冊時選擇認領者或機構身分；登入後回到願望牆，機構與管理員從右上角按鈕進入後台。

### 登入狀態

帳號與密碼由後端自己管理（`com.onlinesanta.auth`），不依賴任何外部身分服務：

- **access token**：後端簽發、15 分鐘有效的 HS256 JWT，前端只放在記憶體，請求帶 `Authorization: Bearer`。
  token 只放使用者 id；角色、信箱是否驗證一律每次請求回資料庫查，所以管理員核准機構、使用者剛完成驗證，
  下一個請求就生效。
- **refresh token**：隨機值，只放在 HttpOnly cookie（`__session`，前端的 JavaScript 讀不到），資料庫存雜湊。
  每次換發都輪替；已撤銷的又被使用，代表 cookie 被複製，整串撤銷。登出、重設密碼、停用帳號都會撤銷。
- 頁面載入時用 cookie 換一張 access token 還原登入；API 回 401（剛好過期）時自動換發並重試一次。
- 連續輸錯密碼 5 次鎖定 15 分鐘；登入失敗一律是「信箱或密碼不正確」，不透露帳號是否存在。

### 信箱驗證

註冊後會寄一組 **6 位數驗證碼**到信箱（15 分鐘有效），在註冊後的畫面、頁首的提示列或個人檔案輸入。
註冊不會檢查註冊者是否真的擁有那個信箱，系統因此有兩條規則：

- **未驗證可以登入、可以瀏覽**，但不能認領、不能申請機構
- **未驗證的帳號只有一般民眾的權限**（`AppPrincipal.effectiveRole()`）

第二條是必要的：角色提升需要驗證過的信箱，但提升是單向的，資料庫的角色不會因為
信箱狀態改變而降級。授權一律看 `effectiveRole()`，不看資料庫的 `role`
——有兩套認定就遲早會有一套漏掉。管理員白名單同樣要求信箱已驗證，否則任何人用白名單上的信箱
註冊就能取得管理員權限。

6 位數只有一百萬種組合，所以：驗證碼錯 5 次就作廢（要重寄）、資料庫存的是 HMAC 而不是明文或單純的雜湊、
重寄兩封之間至少 60 秒、一天最多 5 封。重設密碼用的是 32 bytes 隨機值的連結（60 分鐘有效、只能用一次），
成功後視為信箱已驗證，並撤銷所有裝置的登入。

開發模式讓整個專案不需要寄信就能跑起來——前端設 `VITE_DEV_AUTH=true`，`npm run dev` 後到 `/login`，輸入
任意 email 即可登入或切換身分。開發模式的註冊會顯示所有欄位（含密碼），但密碼不驗證，
只用來檢查畫面；姓名與顯示名稱會儲存。

不設 `VITE_DEV_AUTH` 就走完整的帳號流程。本機沒設定 SMTP（`MAIL_HOST` 空白）時，**驗證碼與重設密碼連結
會印在後端的 log**（找 `[dev-mail]`），照著輸入即可。

## 本地開發的身分模擬

正式環境以 access token 認證。本機開發啟用 `dev-auth` profile（由
`spring.profiles.group` 在 `local` 時自動帶出），可用 `X-Dev-User-Email` 標頭
指定身分，免去每次 curl 都要先註冊、輸入驗證碼取得 token：

```bash
curl http://localhost:8080/api/me -H "X-Dev-User-Email: org@example.org"
```

第一次出現的 email 會自動建立為 `DONOR` 帳號。帶了 `Authorization` 標頭的請求
會跳過這個機制，真實 token 永遠優先。

`dev-auth` 與 `prod` 不可同時啟用，`AuthConfigurationGuard` 會在啟動時檢查，
組合錯誤就讓應用程式起不來。

### 成為管理員

把 email 加進白名單，下次登入即取得 `ADMIN` 角色：

```bash
APP_ADMIN_EMAILS=you@example.com ./mvnw spring-boot:run
```

管理員可在 `/api/admin/organizations` 審核機構註冊。已隸屬機構的帳號不會被提升，
避免同一人既能上架願望又能審核自己的機構。

> **Windows 使用者注意**：Git Bash 把命令列參數傳給 `curl.exe` 時會破壞 UTF-8，
> 含中文的 JSON 請寫成檔案再以 `--data-binary @body.json` 送出。

## 介面與設計系統

公開頁（願望牆、詳情、登入註冊、我的認領、個人資料）使用深色玻璃風格的自製設計系統：
`frontend/src/styles/` 下的 `rd.css`（變數與元件）、`rd-auth.css`（登入註冊）、
`rd-motion.css`（動畫），全部限定在 `.rd` 範圍內；轉場與動畫工具在 `lib/motion.ts`。
機構與管理員後台暫時仍是舊樣式，之後會改版。

## 須知同意與首次登入說明

- **兩份須知**：《認領者參與須知》（註冊與認領前必須同意）與《合作機構參與須知》（機構註冊建立帳號時同意，送出機構申請前後端會再檢查）。同意紀錄存在 `terms_consents`（只增不改，
  每次同意新增一列：使用者、須知、版本、時間），是否需要重新同意看「最新一筆的版本是否
  等於目前版本」。
- **改版須知時**必須同時改兩處，否則前端會顯示「網站內容已更新，請重新整理」並拒絕同意：
  後端 `app.terms.donor-version`／`org-version`（`application.yml`／環境變數 `APP_TERMS_DONOR_VERSION`、
  `APP_TERMS_ORG_VERSION`）與前端 `DONOR_TERMS.version`／`ORG_TERMS.version`
  （`components/terms/termsContent.tsx`，由設計稿的 TERMS 產生，不要手打）。
- **首次登入說明**（「開始之前」）看完後記錄在 `users.onboarded_at`，之後不再自動跳出；
  標頭的 ⓘ 可重看。
- **捐贈收據**：認領者回報寄送時可申請，個資加密儲存，僅管理員可在稽核下檢視。

## 圖片上傳

依敏感度分成兩個 bucket：

| Bucket | 內容 | 存取方式 |
|---|---|---|
| 公開 | 禮物示意圖 | 固定網址，無需簽章 |
| 私密 | 寄送證明、收件證明 | 限時簽章網址（10 分鐘） |

上傳走三步驟，檔案不經過 API：

```
POST /api/uploads/signed-url   → 取得限時直傳網址
PUT  <該網址>                   → 前端直接傳到儲存端
POST /api/attachments/{id}/confirm → 後端向儲存端查證後標記完成
```

本機開發啟用 `dev-storage` profile（隨 `local` 自動帶出），檔案存在
`backend/.local-storage/`，不需要 GCP 專案就能跑完整個流程。

## 逾期認領的處理

去年願望供不應求，認領後遲遲未寄送會讓孩子的願望一直卡著。是否收回由**機構自己
決定**，設定在 `PATCH /api/organizations/me`：

| 政策 | 逾期時的行為 |
|---|---|
| `MANUAL` | 只列入機構後台的逾期清單（`GET /api/organizations/me/claims/overdue`），由機構聯繫後自行決定 |
| `AUTO` | 自動釋回，願望重新上架 |

判斷依據是**認領當下的政策快照**，不是機構現在的設定 —— 已經在準備禮物的人不該
因為機構臨時改設定而被無預警收回。

Cloud Run 是 scale-to-zero 的，沒有常駐行程，`@Scheduled` 不會可靠觸發。改由
**Cloud Scheduler 每日呼叫** `POST /internal/jobs/release-expired-claims`，順帶把
實例喚醒。該端點走獨立的安全鏈，只接受指定服務帳號的 Google OIDC token。

管理員也可隨時手動觸發：`POST /api/admin/jobs/release-expired-claims`。

## 願望上架後出問題怎麼辦

願望一旦被認領，**內容就鎖定、不能修改**（認領者可能已經照內容買好禮物）；只有上架中、
還沒被認領的願望，機構才能「修改並重新送審」。管理員凍結已認領的願望時，認領者仍看得到
自己的認領（顯示「暫停處理中」），寄送期限暫停、解除後順延。完整的情境與流程見
[docs/wish-post-publish-flow.md](docs/wish-post-publish-flow.md)。

## 部署

推到 `main` 就會自動部署（`.github/workflows/deploy.yml`）：測試 → 建置映像檔 →
Cloud Run → Firebase Hosting。認證走 Workload Identity Federation，不使用 JSON 金鑰。

雲端資源的手動前置步驟與備份、隱私規範等維運文件只存在維護者本機，不在 repo 內。

### 冷啟動

Cloud Run 是 scale-to-zero 的，映像檔用 AppCDS 把啟動路徑上的類別預先封存：

| | 啟動時間 |
|---|---|
| 不帶 CDS | 8.4–9.0 秒 |
| 帶 CDS | **5.2–5.3 秒** |

> CDS 有個沒有錯誤訊息的坑：封存檔會記下 classpath，**訓練與執行的工作目錄
> 必須一致**，否則封存檔作廢，而預設的 `-Xshare:auto` 會靜靜地退回一般啟動——
> 看起來一切正常，實際上什麼都沒省到。要驗證只能用 `-Xshare:on`（用不了就直接失敗）。

## 正式環境設定

| 環境變數 | 說明 |
|---|---|
| `APP_AUTH_JWT_SECRET` | 登入 token 的簽章密鑰，至少 32 個字元，沒設定後端不會啟動（`openssl rand -base64 48`） |
| `APP_AUTH_COOKIE_SECURE` | 登入狀態 cookie 是否加 `Secure`；https 的正式環境必須是 `true` |
| `MAIL_HOST` 等 | SMTP 設定。**正式環境必填**：信箱驗證碼與重設密碼都靠它寄出 |
| `APP_ADMIN_EMAILS` | 管理員白名單，逗號分隔 |
| `APP_ALLOWED_ORIGINS` | 允許跨來源請求的前端網域 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Neon PostgreSQL 連線資訊 |
| `GCS_PUBLIC_BUCKET` | 公開 bucket 名稱（禮物示意圖） |
| `GCS_PRIVATE_BUCKET` | 私密 bucket 名稱（寄送證明、收件證明） |
| `RECEIPT_ENCRYPTION_KEY` | 捐贈收據個資的加密金鑰（後端啟動必要） |
| `APP_TERMS_DONOR_VERSION` | 目前《認領者參與須知》版本（見下） |
| `APP_TERMS_ORG_VERSION` | 目前《合作機構參與須知》版本（見下） |
| `INTERNAL_JOB_AUDIENCE` | Cloud Scheduler OIDC token 的 audience，設為本服務網址 |
| `SCHEDULER_SERVICE_ACCOUNT` | 允許觸發排程的服務帳號 email |

> **Cloud Run 上簽章的必要設定**：執行環境的服務帳號沒有私鑰檔，簽章會改走 IAM
> `signBlob` API，因此該服務帳號必須擁有**自己**的
> `roles/iam.serviceAccountTokenCreator`。少了這個權限，程式在本機（有金鑰檔）
> 正常，一上 Cloud Run 就會失敗。
>
> 私密 bucket 還需設定 CORS，允許前端網域的 `PUT`，否則瀏覽器直傳會被擋。

## 專案結構

```
backend/    Spring Boot API（package-by-feature）
frontend/   React + Vite 前端
```

## 設計重點

- **搶領防超賣**：認領走原子條件 UPDATE（樂觀鎖），並以 partial unique index 作為資料庫層最後防線
- **兒童隱私**：schema 層級不存在孩童姓名 / 生日 / 照片欄位，僅有暱稱與年齡區間
- **成本控制**：全鏈路 scale-to-zero，離峰帳單接近 $0
